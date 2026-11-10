package br.com.nexusbuilderlab.semanexus.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthConfigResponse(
        @Schema(description = "true quando o profile demo está ativo (exibe o aviso 'AMBIENTE DEMONSTRATIVO' na tela de login)")
        boolean demo) {
}
