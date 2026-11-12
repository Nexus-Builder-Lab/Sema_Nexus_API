package br.com.nexusbuilderlab.semanexus.core.usuario.dto;

import jakarta.validation.constraints.NotNull;

/** Ativação ou desativação (inativar não apaga o usuário). */
public record AtivacaoRequest(@NotNull Boolean ativo) {
}
