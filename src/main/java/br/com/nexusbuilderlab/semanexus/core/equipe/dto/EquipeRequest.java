package br.com.nexusbuilderlab.semanexus.core.equipe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cadastro/edição de equipe. O responsável, se informado, passa a ser membro da equipe. */
public record EquipeRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotNull Long setorId,
        @Schema(description = "Responsável (usuário ativo); opcional") Long responsavelId,
        Boolean ativo) {
}
