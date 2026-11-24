package br.com.nexusbuilderlab.semanexus.common.geo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Cálculos geoespaciais simples (WGS84, esfera de raio médio) para as ferramentas de agentes: distância de
 * Haversine, caixa envolvente (pré-filtro em SQL) e posição de um ponto em relação a um polígono GeoJSON.
 * Para raios de dezenas de km a aproximação equiretangular local usada nos polígonos tem erro desprezível.
 */
public final class Geodesia {

    public static final double RAIO_TERRA_KM = 6371.0088;
    private static final double KM_POR_GRAU = Math.PI * RAIO_TERRA_KM / 180.0;

    private Geodesia() {
    }

    /** Caixa envolvente em graus: {latMin, latMax, lonMin, lonMax}. */
    public record Caixa(double latMin, double latMax, double lonMin, double lonMax) {
    }

    /** Distância de círculo máximo em km. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * RAIO_TERRA_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** Caixa que contém o círculo de {@code raioKm} (mais {@code margemKm}) em torno do ponto. */
    public static Caixa caixa(double lat, double lon, double raioKm, double margemKm) {
        double r = raioKm + margemKm;
        double dLat = r / KM_POR_GRAU;
        double dLon = r / (KM_POR_GRAU * Math.max(0.01, Math.cos(Math.toRadians(lat))));
        return new Caixa(lat - dLat, lat + dLat, lon - dLon, lon + dLon);
    }

    /** Anéis (exteriores e furos) de um Polygon/MultiPolygon GeoJSON como listas de {lon, lat}; vazia se inválido. */
    @SuppressWarnings("unchecked")
    public static List<List<double[]>> aneis(Map<String, Object> geometria) {
        return aneis(geometria, false);
    }

    /** Como {@link #aneis(Map)}; com {@code soExteriores}, só o primeiro anel de cada polígono (sem furos). */
    @SuppressWarnings("unchecked")
    public static List<List<double[]>> aneis(Map<String, Object> geometria, boolean soExteriores) {
        List<List<double[]>> aneis = new ArrayList<>();
        if (geometria == null || !(geometria.get("coordinates") instanceof List<?> coords)) {
            return aneis;
        }
        try {
            if ("Polygon".equals(geometria.get("type"))) {
                adicionar(aneis, (List<Object>) coords, soExteriores);
            } else if ("MultiPolygon".equals(geometria.get("type"))) {
                for (Object poligono : coords) {
                    adicionar(aneis, (List<Object>) poligono, soExteriores);
                }
            }
        } catch (ClassCastException | NullPointerException e) {
            return new ArrayList<>();
        }
        return aneis;
    }

    @SuppressWarnings("unchecked")
    private static void adicionar(List<List<double[]>> destino, List<Object> aneisDoPoligono, boolean soExterior) {
        for (Object anel : soExterior ? aneisDoPoligono.subList(0, Math.min(1, aneisDoPoligono.size())) : aneisDoPoligono) {
            List<double[]> pontos = new ArrayList<>();
            for (Object p : (List<Object>) anel) {
                List<Object> c = (List<Object>) p;
                pontos.add(new double[] {((Number) c.get(0)).doubleValue(), ((Number) c.get(1)).doubleValue()});
            }
            destino.add(pontos);
        }
    }

   /** Centroide aproximado (média dos vértices dos anéis exteriores de todos os polígonos, sem repetir o vértice de fechamento). */
    public static double[] centroide(Map<String, Object> geometria) {
        double lat = 0;
        double lon = 0;
        int n = 0;
        for (List<double[]> anel : aneis(geometria, true)) {
            int fim = anel.size() > 1 && Arrays.equals(anel.get(0), anel.get(anel.size() - 1)) ? anel.size() - 1 : anel.size();
            for (int k = 0; k < fim; k++) {
                lon += anel.get(k)[0];
                lat += anel.get(k)[1];
                n++;
            }
        }
        return n == 0 ? null : new double[] {lat / n, lon / n};
    }

    /** Caixa envolvente de todos os vértices da geometria, ampliada em {@code raioKm}; {@code null} se inválida. */
    public static Caixa caixaDaGeometria(Map<String, Object> geometria, double raioKm) {
        double latMin = 90, latMax = -90, lonMin = 180, lonMax = -180;
        boolean algum = false;
        for (List<double[]> anel : aneis(geometria, true)) {
            for (double[] p : anel) {
                latMin = Math.min(latMin, p[1]);
                latMax = Math.max(latMax, p[1]);
                lonMin = Math.min(lonMin, p[0]);
                lonMax = Math.max(lonMax, p[0]);
                algum = true;
            }
        }
        if (!algum) {
            return null;
        }
        double dLat = raioKm / KM_POR_GRAU;
        double dLon = raioKm / (KM_POR_GRAU * Math.max(0.01, Math.cos(Math.toRadians(Math.max(Math.abs(latMin), Math.abs(latMax))))));
        return new Caixa(latMin - dLat, latMax + dLat, lonMin - dLon, lonMax + dLon);
    }

    /**
     * Distância aproximada entre duas áreas: 0 se algum vértice de uma está dentro da outra; senão a menor distância
     * de um vértice (amostrado, no máximo ~200 por área) de uma até a outra. Não detecta cruzamento de arestas sem
     * vértice interno (raro). {@code NaN} se alguma geometria é inválida.
     */
    public static double distanciaEntreAreasKm(Map<String, Object> a, Map<String, Object> b) {
        double d1 = distanciaDosVertices(a, b);
        double d2 = distanciaDosVertices(b, a);
        return Double.isNaN(d1) || Double.isNaN(d2) ? Double.NaN : Math.min(d1, d2);
    }

    private static double distanciaDosVertices(Map<String, Object> de, Map<String, Object> para) {
        List<double[]> vertices = new ArrayList<>();
        aneis(de, true).forEach(vertices::addAll);
        if (vertices.isEmpty() || aneis(para).isEmpty()) {
            return Double.NaN;
        }
        int passo = Math.max(1, vertices.size() / 200);
        double min = Double.MAX_VALUE;
        for (int i = 0; i < vertices.size(); i += passo) {
            min = Math.min(min, distanciaAoPoligonoKm(vertices.get(i)[1], vertices.get(i)[0], para));
            if (min == 0) {
                break;
            }
        }
        return min;
    }

    /**
     * Distância em km do ponto à geometria: 0 se o ponto está dentro (respeitando furos), senão a menor distância
     * até uma aresta. {@code NaN} se a geometria é inválida.
     */
    public static double distanciaAoPoligonoKm(double lat, double lon, Map<String, Object> geometria) {
        List<List<double[]>> aneis = aneis(geometria);
        if (aneis.isEmpty()) {
            return Double.NaN;
        }
        if (dentro(lat, lon, aneis)) {
            return 0;
        }
        double kmLon = KM_POR_GRAU * Math.cos(Math.toRadians(lat));
        double min = Double.MAX_VALUE;
        for (List<double[]> anel : aneis) {
            for (int i = 0; i + 1 < anel.size(); i++) {
                double[] a = anel.get(i);
                double[] b = anel.get(i + 1);
                min = Math.min(min, distanciaSegmentoKm((a[0] - lon) * kmLon, (a[1] - lat) * KM_POR_GRAU,
                        (b[0] - lon) * kmLon, (b[1] - lat) * KM_POR_GRAU));
            }
        }
        return min;
    }

    /** Ponto dentro de algum anel externo e fora dos furos (regra par-ímpar sobre todos os anéis). */
    private static boolean dentro(double lat, double lon, List<List<double[]>> aneis) {
        boolean dentro = false;
        for (List<double[]> anel : aneis) {
            for (int i = 0, j = anel.size() - 1; i < anel.size(); j = i++) {
                double xi = anel.get(i)[0];
                double yi = anel.get(i)[1];
                double xj = anel.get(j)[0];
                double yj = anel.get(j)[1];
                if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                    dentro = !dentro;
                }
            }
        }
        return dentro;
    }

    /** Distância da origem (0,0) ao segmento AB, em km (coordenadas já projetadas em km). */
    private static double distanciaSegmentoKm(double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
        return Math.hypot(ax + t * dx, ay + t * dy);
    }
}
