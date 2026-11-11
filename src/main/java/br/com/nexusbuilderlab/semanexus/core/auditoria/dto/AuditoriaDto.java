package br.com.nexusbuilderlab.semanexus.core.auditoria.dto;

import java.time.LocalDateTime;

/** Registro de auditoria exposto pela API. {@code usuarioNome} é nulo para ações sem usuário (sistema/anônimo). */
public record AuditoriaDto(
        Long id,
        LocalDateTime data,
        Long usuarioId,
        String usuarioNome,
        String acao,
        String entidade,
        String entidadeId,
        String detalhe) {
}
