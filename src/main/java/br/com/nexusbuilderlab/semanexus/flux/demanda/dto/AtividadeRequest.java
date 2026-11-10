package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Registro de atividade durante o acompanhamento (a demanda precisa estar Em andamento). */
@Schema(description = "Atividade de acompanhamento registrada no histórico da demanda")
public record AtividadeRequest(
        @Schema(example = "Contato com o proprietário e agendamento da vistoria.") @NotBlank @Size(max = 2000) String descricao,
        @Schema(description = "Data da atividade (padrão: hoje; não pode ser futura)", example = "2026-11-12", nullable = true)
        @PastOrPresent LocalDate data) {
}
