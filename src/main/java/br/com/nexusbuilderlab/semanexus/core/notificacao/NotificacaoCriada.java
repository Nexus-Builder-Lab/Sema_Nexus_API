package br.com.nexusbuilderlab.semanexus.core.notificacao;

import br.com.nexusbuilderlab.semanexus.core.notificacao.dto.NotificacaoDto;

/** Evento interno: notificação gravada para {@code usuarioId} (entregue por SSE só depois do commit — Issue #148). */
public record NotificacaoCriada(Long usuarioId, NotificacaoDto notificacao) {
}
