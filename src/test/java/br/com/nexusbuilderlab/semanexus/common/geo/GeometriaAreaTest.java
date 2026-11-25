package br.com.nexusbuilderlab.semanexus.common.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

public class GeometriaAreaTest {

    @SafeVarargs
    public static Map<String, Object> poligono(List<List<Double>>... aneis) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("type", "Polygon");
        g.put("coordinates", List.of(aneis));
        return g;
    }

    public static List<List<Double>> anel(double lon0, double lat0, double lon1, double lat1) {
        return List.of(List.of(lon0, lat0), List.of(lon1, lat0), List.of(lon1, lat1), List.of(lon0, lat1), List.of(lon0, lat0));
    }

    @Test
    void areaDeUmQuadrilateroConfereComAFormulaFechada() {
        // faixa lat/lon: A = R² · Δλ · (sen φ2 − sen φ1)
        double r = Geodesia.RAIO_TERRA_KM * 1000;
        double esperadoHa = r * r * Math.toRadians(0.1)
                * Math.abs(Math.sin(Math.toRadians(-15.5)) - Math.sin(Math.toRadians(-15.6))) / 10_000;
        GeometriaArea.Resultado res = GeometriaArea.analisar(poligono(anel(-56.1, -15.6, -56.0, -15.5)));
        assertThat(res.areaHa().doubleValue()).isCloseTo(esperadoHa, Offset.offset(0.01));
        assertThat(res.areaHa().doubleValue()).isBetween(11_500.0, 12_300.0);
        assertThat(res.centroide()[0]).isBetween(-15.6, -15.5);
        assertThat(res.centroide()[1]).isBetween(-56.1, -56.0);
    }

    @Test
    void furoESubtraido() {
        BigDecimal cheio = GeometriaArea.analisar(poligono(anel(-56.1, -15.6, -56.0, -15.5))).areaHa();
        BigDecimal comFuro = GeometriaArea.analisar(poligono(anel(-56.1, -15.6, -56.0, -15.5),
                anel(-56.08, -15.58, -56.06, -15.56))).areaHa();
        assertThat(comFuro).isLessThan(cheio);
    }

    @Test
    void multiPolygonSomaAsPartes() {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("type", "MultiPolygon");
        g.put("coordinates", List.of(List.of(anel(-56.1, -15.6, -56.0, -15.5)), List.of(anel(-55.9, -15.6, -55.8, -15.5))));
        BigDecimal um = GeometriaArea.analisar(poligono(anel(-56.1, -15.6, -56.0, -15.5))).areaHa();
        assertThat(GeometriaArea.analisar(g).areaHa().doubleValue()).isCloseTo(um.doubleValue() * 2, Offset.offset(20.0));
    }

    @Test
    void rejeitaGeometriasInvalidas() {
        assertThatThrownBy(() -> GeometriaArea.analisar(poligono(anel(-70, -15.6, -69.9, -15.5))))
                .hasMessageContaining("fora de Mato Grosso");
        assertThatThrownBy(() -> GeometriaArea.analisar(poligono(List.of(List.of(-56.1, -15.6), List.of(-56.0, -15.6),
                List.of(-56.0, -15.5), List.of(-56.1, -15.5))))).hasMessageContaining("não fechado");
        assertThatThrownBy(() -> GeometriaArea.analisar(poligono(List.of(List.of(-56.1, -15.6), List.of(-56.0, -15.6),
                List.of(-56.1, -15.6))))).hasMessageContaining("4 pontos");
        assertThatThrownBy(() -> GeometriaArea.analisar(poligono(List.of(List.of(-56.1, -15.6), List.of(-56.0, Double.NaN),
                List.of(-56.0, -15.5), List.of(-56.1, -15.6))))).isInstanceOf(IllegalArgumentException.class);
        Map<String, Object> ponto = Map.of("type", "Point", "coordinates", List.of(-56.1, -15.6));
        assertThatThrownBy(() -> GeometriaArea.analisar(ponto)).hasMessageContaining("Polygon");
        Map<String, Object> estranha = Map.of("type", "Polygon", "coordinates", List.of("x"));
        assertThatThrownBy(() -> GeometriaArea.analisar(estranha)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeometriaArea.analisar(poligono(List.of(List.of(-56.1, -15.6), List.of(-56.0, -15.6),
                List.of(-56.05, -15.6), List.of(-56.1, -15.6))))).hasMessageContaining("nula");
    }

    @Test
    void rejeitaMaisDe2000Vertices() {
        List<List<Double>> pts = new ArrayList<>();
        for (int i = 0; i < 2001; i++) {
            pts.add(List.of(-56.1 + i * 1e-6, -15.6 + (i % 2) * 1e-3));
        }
        pts.add(pts.get(0));
        assertThatThrownBy(() -> GeometriaArea.analisar(poligono(pts))).hasMessageContaining("2000 vértices");
    }

    @Test
    void toleranciaDe2PorCento() {
        assertThat(GeometriaArea.compativel(new BigDecimal("101.9"), new BigDecimal("100"))).isTrue();
        assertThat(GeometriaArea.compativel(new BigDecimal("103"), new BigDecimal("100"))).isFalse();
    }
}
