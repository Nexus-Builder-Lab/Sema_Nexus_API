package br.com.nexusbuilderlab.semanexus.core.notificacao.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Quantidade de notificações não lidas do usuário logado")
public record ContagemNaoLidas(long naoLidas) {
}
