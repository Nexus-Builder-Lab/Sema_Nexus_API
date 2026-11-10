package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Registro de fiscalização (Em andamento → Aguardando conclusão). Não gera auto de infração. */
@Schema(description = "Registro completo da fiscalização realizada (simulada)")
public record FiscalizacaoRequest(
        @Schema(description = "Data da fiscalização (não pode ser futura)", example = "2026-11-12")
        @NotNull @PastOrPresent LocalDate data,
        @Schema(description = "Atividades realizadas") @NotBlank @Size(max = 4000) String atividades,
        @Schema(description = "Resultado da fiscalização") @NotBlank @Size(max = 4000) String resultado,
        @Schema(description = "Evidências registradas (texto)") @NotBlank @Size(max = 4000) String evidencias,
        @Schema(nullable = true) @Size(max = 4000) String recomendacoes) {
}
