package br.com.nexusbuilderlab.semanexus.common.geo;

import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteLoader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validação e cálculo de área de um polígono GeoJSON (WGS84) enviado pelo cliente: tipo Polygon/MultiPolygon,
 * anéis fechados com ao menos 4 pontos, coordenadas finitas dentro da caixa de MT e limites de tamanho. A área em
 * hectares é calculada no servidor (esfera de raio médio, fórmula de área equivalente de Chamberlain-Duquette);
 * furos (anéis interiores) são subtraídos.
 */
public final class GeometriaArea {

    public static final int MAX_VERTICES = 2000;
    public static final int MAX_BYTES = 200 * 1024;
    /** Tolerância relativa entre a área informada pelo cliente e a calculada. */
    public static final double TOLERANCIA = 0.02;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final double M2_POR_HA = 10_000.0;

    private GeometriaArea() {
    }

    /** Resultado: texto JSON canônico, área (ha, 2 casas) e centroide {lat, lon}. */
    public record Resultado(String json, BigDecimal areaHa, double[] centroide) {
    }

    /** Valida e calcula; lança {@link IllegalArgumentException} com mensagem em português se inválida. */
    public static Resultado analisar(Map<String, Object> geometria) {
        if (geometria == null) {
            throw new IllegalArgumentException("Geometria ausente.");
        }
        String json;
        try {
            json = JSON.writeValueAsString(geometria);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Geometria não é um JSON válido.");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Geometria excede " + (MAX_BYTES / 1024) + " KB.");
        }
        Object tipo = geometria.get("type");
        if (!"Polygon".equals(tipo) && !"MultiPolygon".equals(tipo)) {
            throw new IllegalArgumentException("A geometria deve ser Polygon ou MultiPolygon (GeoJSON).");
        }
        if (!(geometria.get("coordinates") instanceof List<?> coords) || coords.isEmpty()) {
            throw new IllegalArgumentException("A geometria não tem coordenadas.");
        }
        List<List<List<double[]>>> poligonos = new ArrayList<>();
        int[] vertices = {0};
        try {
            if ("Polygon".equals(tipo)) {
                poligonos.add(lerPoligono(coords, vertices));
            } else {
                for (Object p : coords) {
                    if (!(p instanceof List<?> lp) || lp.isEmpty()) {
                        throw new IllegalArgumentException("Polígono vazio no MultiPolygon.");
                    }
                    poligonos.add(lerPoligono(lp, vertices));
                }
            }
        } catch (ClassCastException | NullPointerException | IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Estrutura de coordenadas inválida (esperado [lon, lat]).");
        }
        double m2 = 0;
        for (List<List<double[]>> aneis : poligonos) {
            for (int i = 0; i < aneis.size(); i++) {
                double a = areaAnelM2(aneis.get(i));
                m2 += i == 0 ? a : -a;
            }
        }
        BigDecimal ha = BigDecimal.valueOf(m2 / M2_POR_HA).setScale(2, RoundingMode.HALF_UP);
        if (m2 <= 0 || ha.signum() <= 0) {
            throw new IllegalArgumentException("A área calculada da geometria é nula (mínimo 0,01 ha).");
        }
        return new Resultado(json, ha, Geodesia.centroide(geometria));
    }

    /** Área informada pelo cliente compatível com a calculada (até {@link #TOLERANCIA}, ou 0,01 ha). */
    public static boolean compativel(BigDecimal informada, BigDecimal calculada) {
        double c = calculada.doubleValue();
        double diff = Math.abs(informada.doubleValue() - c);
        return diff <= Math.max(0.01, c * TOLERANCIA);
    }

    public static boolean dentroDeMt(double lat, double lon) {
        return lat >= ReferenciaSateliteLoader.LAT_MIN.doubleValue() && lat <= ReferenciaSateliteLoader.LAT_MAX.doubleValue()
                && lon >= ReferenciaSateliteLoader.LON_MIN.doubleValue() && lon <= ReferenciaSateliteLoader.LON_MAX.doubleValue();
    }

    private static List<List<double[]>> lerPoligono(List<?> aneis, int[] vertices) {
        List<List<double[]>> saida = new ArrayList<>();
        for (Object anel : aneis) {
            List<?> pts = (List<?>) anel;
            if (pts.size() < 4) {
                throw new IllegalArgumentException("Cada anel precisa de ao menos 4 pontos (fechado).");
            }
            List<double[]> ps = new ArrayList<>();
            for (Object p : pts) {
                List<?> c = (List<?>) p;
                if (c.size() < 2 || !(c.get(0) instanceof Number) || !(c.get(1) instanceof Number)) {
                    throw new IllegalArgumentException("Coordenada inválida (esperado [lon, lat]).");
                }
                double lon = ((Number) c.get(0)).doubleValue();
                double lat = ((Number) c.get(1)).doubleValue();
                if (!Double.isFinite(lon) || !Double.isFinite(lat)) {
                    throw new IllegalArgumentException("Coordenada não numérica (NaN/infinito).");
                }
                if (!dentroDeMt(lat, lon)) {
                    throw new IllegalArgumentException("Há vértice fora de Mato Grosso (lat -18,1 a -7,3; lon -61,7 a -50,1).");
                }
                if (++vertices[0] > MAX_VERTICES) {
                    throw new IllegalArgumentException("A geometria excede " + MAX_VERTICES + " vértices.");
                }
                ps.add(new double[] {lon, lat});
            }
            double[] a = ps.get(0);
            double[] b = ps.get(ps.size() - 1);
            if (a[0] != b[0] || a[1] != b[1]) {
                throw new IllegalArgumentException("Anel não fechado (primeiro e último ponto devem coincidir).");
            }
            saida.add(ps);
        }
        return saida;
    }

    /** Área absoluta (m²) de um anel na esfera: R²/2 · |Σ (λ2-λ1)(2 + sen φ1 + sen φ2)|. */
    static double areaAnelM2(List<double[]> anel) {
        double soma = 0;
        for (int i = 0; i + 1 < anel.size(); i++) {
            double[] p1 = anel.get(i);
            double[] p2 = anel.get(i + 1);
            soma += Math.toRadians(p2[0] - p1[0]) * (2 + Math.sin(Math.toRadians(p1[1])) + Math.sin(Math.toRadians(p2[1])));
        }
        double r = Geodesia.RAIO_TERRA_KM * 1000.0;
        return Math.abs(soma) * r * r / 2.0;
    }
}
