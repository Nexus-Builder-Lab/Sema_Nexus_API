package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Conclusão (Aguardando conclusão → Finalizada). */
@Schema(description = "Conclusão da demanda pelo Gestor/Admin")
public record ConclusaoRequest(
        @Schema(description = "Resultado final") @NotBlank @Size(max = 4000) String resultadoFinal,
        @Schema(description = "Justificativa da conclusão") @NotBlank @Size(max = 2000) String justificativa) {
}
