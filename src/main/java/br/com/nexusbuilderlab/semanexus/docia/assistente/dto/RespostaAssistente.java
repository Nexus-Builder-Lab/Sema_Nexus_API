package br.com.nexusbuilderlab.semanexus.docia.assistente.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resposta do assistente: texto, modo (identificação do provider), referências citadas e — desde a Issue #108 —
 * o motor ({@code agente}: LOCAL, CLAUDE, DEMONSTRATIVO ou EXTERNO), o modelo e os passos do agente.
 */
public record RespostaAssistente(
        String resposta,
        @Schema(description = "Identificação legível do modo de resposta") String modo,
        List<ReferenciaDocumento> referencias,
        @Schema(description = "LOCAL (agente com IA local), CLAUDE, DEMONSTRATIVO (extrativo, sem IA) ou EXTERNO",
                example = "LOCAL") String agente,
        @Schema(description = "Modelo usado pelo agente; nulo sem IA", nullable = true, example = "qwen3:4b-instruct")
        String modelo,
        @Schema(description = "O que o agente fez para chegar à resposta") List<String> passos) {

    /** Respostas sem agente (demonstrativa extrativa ou provider externo). */
    public RespostaAssistente(String resposta, String modo, List<ReferenciaDocumento> referencias) {
        this(resposta, modo, referencias, "Provider externo configurado".equals(modo) ? "EXTERNO" : "DEMONSTRATIVO",
                null, List.of());
    }
}
