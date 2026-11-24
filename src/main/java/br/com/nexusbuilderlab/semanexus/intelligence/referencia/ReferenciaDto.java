package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** DTOs da camada de referência (dados públicos do INPE). Toda resposta traz fonte, oficialSema e atualizadoEm. */
public final class ReferenciaDto {

    public static final String FONTE = "INPE — dados públicos";
    public static final String AVISO = "Dados públicos reais do INPE (Programa Queimadas / TerraBrasilis), "
            + "camada de referência separada das demandas fictícias. Não são dados oficiais da SEMA-MT.";

    private ReferenciaDto() {
    }

    @Schema(description = "Uma importação (snapshot) de dados públicos")
    public record Importacao(Long id, String fonte, String tipo, LocalDate periodoDe, LocalDate periodoAte,
                             LocalDateTime importadoEm, long registros, String urlOrigem, String licenca,
                             String arquivo) {
    }

    @Schema(description = "Fontes, período, data de importação, quantidade e licença da camada de referência")
    public record Metadados(@Schema(example = "INPE — dados públicos") String fonte,
                            @Schema(example = "false") boolean oficialSema,
                            LocalDateTime atualizadoEm, String aviso, List<Importacao> importacoes) {
    }

    /** Observação de área do foco de calor: a fonte não fornece área (nunca inventar hectares). */
    public static final String AREA_FOCO_NAO_INFORMADA = "Área afetada: não informada pela fonte (o INPE fornece o "
            + "foco de calor como ponto — centro do pixel de detecção do satélite —, sem área queimada).";

    @Schema(description = "Ponto de satélite (foco de calor ou alerta DETER); data/hora em UTC")
    public record Ponto(Long id, String tipo, LocalDateTime dataHora, BigDecimal latitude, BigDecimal longitude,
                        Integer municipioId, String municipio, String bioma, String satelite, String classe,
                        @Schema(description = "Área informada pela fonte (km²); só alertas DETER", nullable = true)
                        BigDecimal areaKm2,
                        @Schema(description = "areaKm2 convertida para hectares (1 km² = 100 ha)", nullable = true,
                                example = "13.2") BigDecimal areaHa) {
    }

    @Schema(description = "Detalhe de um ponto de satélite do INPE para o painel do mapa: todos os campos guardados, "
            + "área (só quando a fonte informa) e polígono do alerta DETER")
    public record PontoDetalhe(@Schema(example = "INPE — dados públicos") String fonte,
                               @Schema(example = "false") boolean oficialSema,
                               String aviso,
                               Ponto ponto,
                               @Schema(description = "true quando a fonte informa a área (alertas DETER)")
                               boolean areaInformada,
                               @Schema(description = "Campo da fonte de onde veio a área (km²): areatotalkm ou "
                                       + "areamunkm", nullable = true, example = "areamunkm") String areaCampoFonte,
                               @Schema(description = "Valor da área exatamente como a fonte informa (km², sem "
                                       + "arredondamento); ponto.areaHa é calculada a partir dele", nullable = true,
                                       example = "0.13200473633075194") BigDecimal areaKm2Fonte,
                               @Schema(description = "Explicação da área exibida (origem ou motivo de não haver área)")
                               String areaObservacao,
                               @Schema(description = "Polígono GeoJSON (WGS84) do alerta como a fonte o fornece; "
                                       + "null para focos de calor (a fonte entrega ponto)", nullable = true,
                                       type = "object") Map<String, Object> geometria,
                               @Schema(description = "Todos os atributos originais da fonte (nomes da fonte)",
                                       nullable = true, type = "object") Map<String, Object> atributos,
                               @Schema(description = "Importação (snapshot) de origem: fonte, período, URL e licença")
                               Importacao importacao) {
    }

    @Schema(description = "Pontos de satélite com a atribuição da fonte")
    public record PontosResponse(@Schema(example = "INPE — dados públicos") String fonte,
                                 @Schema(example = "false") boolean oficialSema,
                                 LocalDateTime atualizadoEm, String aviso,
                                 @Schema(description = "Limite efetivamente aplicado") int limite,
                                 @Schema(description = "true se havia mais pontos que o limite") boolean truncado,
                                 List<Ponto> pontos) {
    }

    public record ContagemMunicipio(Integer municipioId, String municipio, String bioma, long total) {
    }

    @Schema(description = "Contagem de pontos de satélite por município, com a atribuição da fonte")
    public record PorMunicipioResponse(@Schema(example = "INPE — dados públicos") String fonte,
                                       @Schema(example = "false") boolean oficialSema,
                                       LocalDateTime atualizadoEm, String aviso, long total,
                                       List<ContagemMunicipio> itens) {
    }
}
