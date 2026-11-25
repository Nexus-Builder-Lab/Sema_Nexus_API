package br.com.nexusbuilderlab.semanexus.intelligence.camada;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTOs das camadas públicas abertas offline de Mato Grosso (UCs MMA/CNUC, Terras Indígenas FUNAI e hidrografia
 * principal ANA). Todas as respostas trazem fonte, licença, data de referência e {@code oficialSema=false}.
 */
public final class CamadaPublicaDto {

    public static final String AVISO = "Dados públicos abertos (MMA/CNUC, FUNAI e ANA/SNIRH) em camada de referência "
            + "offline para Mato Grosso, separados das demandas fictícias. Não são dados oficiais da SEMA-MT.";

    private CamadaPublicaDto() {
    }

    @Schema(name = "ImportacaoCamadaPublica", description = "Registro de importação offline de uma camada pública")
    public record Importacao(
            Long id,
            TipoCamadaPublica camada,
            String fonte,
            LocalDate dataReferencia,
            LocalDateTime importadoEm,
            long registros,
            String urlOrigem,
            String licenca,
            String arquivo) {
    }

    @Schema(name = "MetadadosCamadasPublicas", description = "Metadados de origem, data e licença das camadas públicas abertas offline")
    public record Metadados(
            @Schema(example = "false") boolean oficialSema,
            LocalDateTime atualizadoEm,
            String aviso,
            List<Importacao> importacoes) {
    }

    @Schema(description = "Feição GeoJSON individual de camada pública")
    public record FeatureGeoJson(
            @Schema(example = "Feature") String type,
            Long id,
            Map<String, Object> properties,
            Map<String, Object> geometry) {
    }

    @Schema(description = "Coleção GeoJSON (WGS84) pronta para renderização offline no mapa")
    public record FeatureCollection(
            @Schema(example = "FeatureCollection") String type,
            List<FeatureGeoJson> features) {
    }

    @Schema(description = "Resposta GeoJSON de uma camada pública com atribuição, licença e oficialSema=false")
    public record GeoJsonResponse(
            TipoCamadaPublica camada,
            String fonte,
            String licenca,
            LocalDate dataReferencia,
            LocalDateTime atualizadoEm,
            @Schema(example = "false") boolean oficialSema,
            String aviso,
            int total,
            boolean truncado,
            FeatureCollection colecao) {
    }

    @Schema(description = "Resumo da fonte e licença de uma camada consultada")
    public record FonteResumo(
            TipoCamadaPublica camada,
            String fonte,
            String licenca,
            LocalDate dataReferencia) {
    }

    @Schema(description = "Feição pública sobreposta ou próxima à localização consultada")
    public record ItemSobreposicao(
            Long id,
            TipoCamadaPublica camada,
            @Schema(nullable = true) String codigoOrigem,
            String nome,
            @Schema(nullable = true) String categoria,
            @Schema(nullable = true) String esfera,
            @Schema(nullable = true) BigDecimal areaHa,
            @Schema(description = "true quando a coordenada ou área da consulta intersecta a feição") boolean dentro,
            @Schema(description = "Distância aproximada em km (0.0 quando há interseção)") double distanciaKm,
            String fonte,
            String licenca,
            LocalDate dataReferencia,
            @Schema(example = "false") boolean oficialSema) {
    }

    @Schema(description = "Síntese da análise de sobreposição com camadas públicas")
    public record ResumoSobreposicao(
            boolean dentroUc,
            @Schema(nullable = true) Double ucMaisProximaKm,
            boolean dentroTerraIndigena,
            @Schema(nullable = true) Double terraIndigenaMaisProximaKm,
            @Schema(nullable = true) Double cursoAguaMaisProximoKm,
            int totalUcsNoRaio,
            int totalTerrasIndigenasNoRaio,
            int totalCursosAguaNoRaio,
            List<FonteResumo> fontes) {
    }

    @Schema(description = "Resultado da consulta de sobreposição/proximidade com UCs, Terras Indígenas e hidrografia principal de MT")
    public record SobreposicaoCamadasPublicas(
            @Schema(example = "false") boolean oficialSema,
            String aviso,
            @Schema(description = "COORDENADA, AREA, PONTO ou SEM_COORDENADA", example = "COORDENADA") String referencia,
            @Schema(nullable = true) Double latitude,
            @Schema(nullable = true) Double longitude,
            double raioKm,
            List<ItemSobreposicao> unidadesConservacao,
            List<ItemSobreposicao> terrasIndigenas,
            List<ItemSobreposicao> hidrografia,
            ResumoSobreposicao resumo) {
    }
}
