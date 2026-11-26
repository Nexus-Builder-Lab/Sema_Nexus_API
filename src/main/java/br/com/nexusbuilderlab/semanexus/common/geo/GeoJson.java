package br.com.nexusbuilderlab.semanexus.common.geo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final ObjectMapper JSON_CANONICO = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
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

    /**
     * Resumo compacto e determinístico de um GeoJSON de área (tipo, contagem de vértices e hash curto canônico) para
     * caber na trilha de auditoria antes/depois sem truncar diferenças no meio do polígono.
     */
    public static String resumo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        Map<String, Object> geo = geometria(texto);
        if (geo == null) {
            return texto;
        }
        String tipo = String.valueOf(geo.get("type"));
        int vertices = contarVertices(geo.get("coordinates"));
        return tipo + " (" + vertices + " vértices, #" + hashCurto(canonico(geo, texto)) + ")";
    }

    private static int contarVertices(Object no) {
        if (!(no instanceof List<?> lista) || lista.isEmpty()) {
            return 0;
        }
        if (lista.get(0) instanceof Number) {
            return 1;
        }
        int total = 0;
        for (Object item : lista) {
            total += contarVertices(item);
        }
        return total;
    }

    private static String canonico(Map<String, Object> geo, String fallback) {
        try {
            return JSON_CANONICO.writeValueAsString(geo);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    private static String hashCurto(String texto) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }
}
