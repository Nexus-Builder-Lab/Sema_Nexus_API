package br.com.nexusbuilderlab.semanexus.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Referência resumida a uma equipe. */
@Schema(description = "Referência resumida a uma equipe")
public record EquipeRef(
        @Schema(example = "1") Long id,
        @Schema(example = "Equipe Alfa (demonstração)") String nome) {
}
