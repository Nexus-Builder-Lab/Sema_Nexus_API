package br.com.nexusbuilderlab.semanexus.core.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AuthConfigResponse(
        @Schema(description = "true quando o profile demo está ativo (exibe o aviso 'AMBIENTE DEMONSTRATIVO' na tela de login)")
        boolean demo,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Cartões do Modo demonstração; presente somente quando demo=true")
        List<PerfilDemo> perfisDemo) {

    public AuthConfigResponse(boolean demo) {
        this(demo, null);
    }
}
