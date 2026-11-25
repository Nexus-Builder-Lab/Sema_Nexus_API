package br.com.nexusbuilderlab.semanexus.common.geo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utilitários de área e geometria GeoJSON (WGS84) usados no mapa: leitura do texto guardado em CLOB e conversão
 * de km² (unidade das fontes do INPE) para hectares. Nunca calcula área para dados reais: a área do INPE é sempre a
 * informada pela fonte.
 */
public final class GeoJson {

    /** 1 km² = 100 ha. */
    public static final BigDecimal HECTARES_POR_KM2 = BigDecimal.valueOf(100);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> TIPOS_AREA = Set.of("Polygon", "MultiPolygon");
    private static final Set<String> TIPOS_QUALQUER = Set.of("Polygon", "MultiPolygon", "LineString", "MultiLineString");

    private GeoJson() {
    }

    /** km² → hectares, com 2 casas decimais; {@code null} se a área não foi informada. */
    public static BigDecimal km2ParaHectares(BigDecimal km2) {
        return km2 == null ? null : km2.multiply(HECTARES_POR_KM2).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Converte o texto GeoJSON guardado no banco em objeto para a resposta. Devolve {@code null} se vazio, inválido
     * ou se não for Polygon/MultiPolygon (o mapa só desenha áreas).
     */
    public static Map<String, Object> geometria(String texto) {
        return lerGeometria(texto, TIPOS_AREA);
    }

    /**
     * Converte o texto GeoJSON (Polygon, MultiPolygon, LineString ou MultiLineString) guardado no banco em objeto
     * para a resposta de camadas geográficas. Devolve {@code null} se vazio ou inválido.
     */
    public static Map<String, Object> geometriaQualquer(String texto) {
        return lerGeometria(texto, TIPOS_QUALQUER);
    }

    private static Map<String, Object> lerGeometria(String texto, Set<String> permitidos) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> g = JSON.readValue(texto, new TypeReference<LinkedHashMap<String, Object>>() { });
            return permitidos.contains(g.get("type")) && g.get("coordinates") != null ? g : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** Objeto JSON (atributos da fonte) guardado em CLOB → mapa ordenado; {@code null} se vazio ou inválido. */
    public static Map<String, Object> objeto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(texto, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
