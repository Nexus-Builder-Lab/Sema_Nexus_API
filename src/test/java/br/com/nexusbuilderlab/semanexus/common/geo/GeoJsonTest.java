package br.com.nexusbuilderlab.semanexus.common.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GeoJsonTest {

    @Test
    void converteKm2ParaHectaresComDuasCasas() {
        assertThat(GeoJson.km2ParaHectares(new BigDecimal("0.1320"))).isEqualByComparingTo("13.20");
        assertThat(GeoJson.km2ParaHectares(new BigDecimal("1.5863"))).isEqualByComparingTo("158.63");
        assertThat(GeoJson.km2ParaHectares(new BigDecimal("0.0876"))).isEqualByComparingTo("8.76");
        assertThat(GeoJson.km2ParaHectares(BigDecimal.ONE).scale()).isEqualTo(2);
        assertThat(GeoJson.km2ParaHectares(null)).isNull();
    }

    @Test
    void geometriaAceitaSoPoligonosValidos() {
        assertThat(GeoJson.geometria("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[-55,-12],[-54,-12],[-54,-11],"
                + "[-55,-12]]]]}")).containsEntry("type", "MultiPolygon");
        assertThat(GeoJson.geometria("{\"type\":\"Point\",\"coordinates\":[-55,-12]}")).isNull();
        assertThat(GeoJson.geometria("{\"type\":\"Polygon\"}")).isNull();
        assertThat(GeoJson.geometria("não é json")).isNull();
        assertThat(GeoJson.geometria(null)).isNull();
        assertThat(GeoJson.geometria(" ")).isNull();
    }

    @Test
    void objetoPreservaAtributosEOrdem() {
        assertThat(GeoJson.objeto("{\"b\":1,\"a\":\"x\"}")).containsExactly(
                java.util.Map.entry("b", 1), java.util.Map.entry("a", "x"));
        assertThat(GeoJson.objeto("[1]")).isNull();
        assertThat(GeoJson.objeto(null)).isNull();
    }
}
