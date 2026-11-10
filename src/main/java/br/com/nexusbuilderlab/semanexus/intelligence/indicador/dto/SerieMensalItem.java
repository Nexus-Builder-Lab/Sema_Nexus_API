package br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Um mês da série de demandas cadastradas e finalizadas. */
@Schema(description = "Demandas cadastradas e finalizadas em um mês")
public record SerieMensalItem(
        @Schema(description = "Mês no formato AAAA-MM", example = "2026-10") String mes,
        @Schema(description = "Demandas recebidas no mês", example = "14") long cadastradas,
        @Schema(description = "Demandas concluídas no mês", example = "9") long finalizadas) {
}
