package br.com.nexusbuilderlab.semanexus.agentes.governanca;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Feedback opcional do autor da pergunta sobre uma resposta do Assistente DocIA (Issue #131). */
@Schema(description = "Feedback opcional sobre uma resposta do Assistente DocIA")
public record AvaliacaoRespostaRequest(
        @Schema(description = "UTIL ou IMPRECISA", example = "UTIL") @NotNull Avaliacao avaliacao) {

    public enum Avaliacao { UTIL, IMPRECISA }
}
