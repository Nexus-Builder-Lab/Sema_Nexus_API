package br.com.nexusbuilderlab.semanexus.docia.comparacao.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Par de documentos a comparar")
public record ComparacaoRequest(@NotNull Long aId, @NotNull Long bId) {
}
