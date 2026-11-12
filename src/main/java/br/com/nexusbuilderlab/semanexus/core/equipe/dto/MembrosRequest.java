package br.com.nexusbuilderlab.semanexus.core.equipe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Substitui o conjunto de membros da equipe. */
public record MembrosRequest(
        @Schema(description = "IDs dos usuários ativos que formam a equipe (substitui a lista atual)")
        @NotNull List<Long> usuarioIds,
        @Schema(description = "Responsável; se ausente mantém o atual (removido se deixar de ser membro)")
        Long responsavelId) {
}
