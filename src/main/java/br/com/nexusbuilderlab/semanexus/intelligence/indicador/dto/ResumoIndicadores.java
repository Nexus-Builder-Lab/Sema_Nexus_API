package br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Totais gerais (cards do dashboard). Sempre sobre dados simulados na demonstração. */
@Schema(description = "Totais gerais das demandas e documentos (dados simulados)")
public record ResumoIndicadores(
        @Schema(description = "Sempre true: indicadores calculados sobre dados fictícios", example = "true") boolean demonstrativo,
        @Schema(example = "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS") String aviso,
        @Schema(example = "120") long totalDemandas,
        @Schema(description = "Status PENDENTE", example = "15") long pendentes,
        @Schema(description = "Status EM_ANDAMENTO", example = "22") long emAndamento,
        @Schema(description = "Status FINALIZADA", example = "58") long finalizadas,
        @Schema(description = "Derivado: prazo < hoje e status ≠ FINALIZADA", example = "9") long atrasadas,
        @Schema(description = "Documentos na biblioteca", example = "47") long totalDocumentos,
        @Schema(description = "Fiscalizações registradas nas demandas filtradas", example = "30") long fiscalizacoes,
        @Schema(description = "Finalizadas / total, em % (0 sem demandas)", example = "48.3") double percentualConclusao,
        @Schema(description = "Média simulada, em dias, entre o recebimento e a conclusão das finalizadas (nulo sem finalizadas)",
                nullable = true, example = "12.5") Double tempoMedioAtendimentoDias) {
}
