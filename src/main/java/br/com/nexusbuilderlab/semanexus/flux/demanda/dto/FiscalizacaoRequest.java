package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

/** Registro de fiscalização (Em andamento → Aguardando conclusão). Não gera auto de infração. */
@Schema(description = "Registro completo da fiscalização realizada (simulada)")
public record FiscalizacaoRequest(
        @Schema(description = "Data da fiscalização (não pode ser futura)", example = "2026-11-12")
        @NotNull @PastOrPresent LocalDate data,
        @Schema(description = "Atividades realizadas") @NotBlank @Size(max = 4000) String atividades,
        @Schema(description = "Resultado da fiscalização") @NotBlank @Size(max = 4000) String resultado,
        @Schema(description = "Evidências registradas (texto)") @NotBlank @Size(max = 4000) String evidencias,
        @Schema(nullable = true) @Size(max = 4000) String recomendacoes,
        @Schema(description = "Checklist estruturado (Issue #145): respostas por id do item do modelo da categoria da "
                + "demanda (`GET /api/demandas/checklists`). Item desconhecido, obrigatório ausente ou tipo errado → 400. "
                + "Opcional (registros sem checklist continuam aceitos).", nullable = true,
                example = "{\"local_acessivel\": true, \"supressao_confirmada\": true, \"area_estimada_ha\": 12.5, "
                        + "\"maquinario_no_local\": false}")
        Map<String, Object> checklist) {

    /** Registro sem checklist (compatível com o contrato anterior à Issue #145). */
    public FiscalizacaoRequest(LocalDate data, String atividades, String resultado, String evidencias,
                               String recomendacoes) {
        this(data, atividades, resultado, evidencias, recomendacoes, null);
    }
}
