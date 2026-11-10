package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Devolução (Aguardando conclusão → Em andamento) para ajustes na fiscalização. */
@Schema(description = "Devolução da demanda ao fiscal para ajustes")
public record DevolucaoRequest(
        @Schema(description = "O que precisa ser ajustado") @NotBlank @Size(max = 2000) String justificativa) {
}
