package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    @Schema(description = "Ponto de satélite (foco de calor ou alerta DETER); data/hora em UTC")
    public record Ponto(Long id, String tipo, LocalDateTime dataHora, BigDecimal latitude, BigDecimal longitude,
                        Integer municipioId, String municipio, String bioma, String satelite, String classe,
                        BigDecimal areaKm2) {
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
