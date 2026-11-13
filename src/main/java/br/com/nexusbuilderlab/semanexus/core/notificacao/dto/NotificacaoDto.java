package br.com.nexusbuilderlab.semanexus.core.notificacao.dto;

import br.com.nexusbuilderlab.semanexus.core.notificacao.Notificacao;
import br.com.nexusbuilderlab.semanexus.core.notificacao.TipoNotificacao;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Notificação do usuário logado (dados fictícios na demonstração)")
public record NotificacaoDto(
        Long id,
        TipoNotificacao tipo,
        String titulo,
        String mensagem,
        @Schema(nullable = true) Long demandaId,
        boolean lida,
        LocalDateTime criadaEm,
        @Schema(nullable = true) LocalDateTime lidaEm) {

    public static NotificacaoDto de(Notificacao n) {
        return new NotificacaoDto(n.getId(), n.getTipo(), n.getTitulo(), n.getMensagem(), n.getDemandaId(),
                n.isLida(), n.getCriadaEm(), n.getLidaEm());
    }
}
