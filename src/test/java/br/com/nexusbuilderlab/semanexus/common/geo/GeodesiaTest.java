package br.com.nexusbuilderlab.semanexus.common.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GeodesiaTest {

    /** Quadrado de 0,1 grau (cerca de 11 km) em torno de (-10, -55), com um furo central. */
    private static final Map<String, Object> POLIGONO = Map.of("type", "Polygon", "coordinates", List.of(
            List.of(List.of(-55.05, -10.05), List.of(-54.95, -10.05), List.of(-54.95, -9.95), List.of(-55.05, -9.95),
                    List.of(-55.05, -10.05)),
            List.of(List.of(-55.01, -10.01), List.of(-54.99, -10.01), List.of(-54.99, -9.99), List.of(-55.01, -9.99),
                    List.of(-55.01, -10.01))));

    @Test
    void haversineUmGrauDeLatitudeTem111Km() {
        assertThat(Geodesia.haversineKm(-10, -55, -11, -55)).isBetween(111.0, 111.4);
        assertThat(Geodesia.haversineKm(-10, -55, -10, -55)).isZero();
    }

    @Test
    void caixaContemOCirculo() {
        var c = Geodesia.caixa(-10, -55, 10, 0);
        assertThat(c.latMax() - c.latMin()).isBetween(0.17, 0.19);
        assertThat(c.lonMin()).isLessThan(-55.0).isGreaterThan(-55.2);
    }

    @Test
    void pontoDentroDoPoligonoTemDistanciaZeroEFuroConta() {
        assertThat(Geodesia.distanciaAoPoligonoKm(-10.03, -55.0, POLIGONO)).isZero();
        // dentro do furo: fora do poligono, a cerca de 1 km da borda do furo
        assertThat(Geodesia.distanciaAoPoligonoKm(-10.0, -55.0, POLIGONO)).isBetween(1.0, 2.5);
    }

    @Test
    void pontoForaTemDistanciaAteAAresta() {
        // 0,1 grau de longitude a leste da borda (cerca de 10,9 km na latitude -10)
        double km = Geodesia.distanciaAoPoligonoKm(-10.0, -54.85, POLIGONO);
        assertThat(km).isBetween(10.5, 11.3);
    }

    @Test
    void geometriaInvalidaDaNaN() {
        assertThat(Geodesia.distanciaAoPoligonoKm(0, 0, null)).isNaN();
        assertThat(Geodesia.centroide(Map.of("type", "Point"))).isNull();
        assertThat(Geodesia.centroide(POLIGONO)[0]).isBetween(-10.1, -9.9);
    }
}
