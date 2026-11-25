package br.com.nexusbuilderlab.semanexus.docia.assistente.dto;

import java.util.List;

import br.com.nexusbuilderlab.semanexus.agentes.AcaoSugerida;
import br.com.nexusbuilderlab.semanexus.docia.vetorial.Recuperacao;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resposta do assistente: texto, modo (identificação do provider), referências citadas e — desde a Issue #108 —
 * o motor ({@code agente}: LOCAL, CLAUDE, DEMONSTRATIVO ou EXTERNO), o modelo e os passos do agente. Desde a Issue
 * #141, {@code recuperacao} diz como os documentos foram encontrados (LEXICAL ou HIBRIDA).
 */
public record RespostaAssistente(
        String resposta,
        @Schema(description = "Identificação legível do modo de resposta") String modo,
        List<ReferenciaDocumento> referencias,
        @Schema(description = "LOCAL (agente com IA local), CLAUDE, DEMONSTRATIVO (extrativo, sem IA) ou EXTERNO",
                example = "LOCAL") String agente,
        @Schema(description = "Modelo usado pelo agente; nulo sem IA", nullable = true, example = "qwen3:4b-instruct")
        String modelo,
        @Schema(description = "O que o agente fez para chegar à resposta") List<String> passos,
        @Schema(description = "Como os documentos foram recuperados: LEXICAL (só busca textual por termos) ou "
                + "HIBRIDA (busca textual + vetorial com embeddings locais, fundidas por RRF). Nula quando nenhuma "
                + "busca foi feita (ex.: pergunta recusada).", nullable = true, example = "HIBRIDA")
        Recuperacao recuperacao,
        @Schema(description = "Action Cards opcionais derivados no servidor do que o agente citou (Issue #136); "
                + "vazio quando não há. Nada é gravado: a pessoa confirma na tela.")
        List<AcaoSugerida> acoesSugeridas) {

    public RespostaAssistente(String resposta, String modo, List<ReferenciaDocumento> referencias, String agente,
                              String modelo, List<String> passos) {
        this(resposta, modo, referencias, agente, modelo, passos, null, List.of());
    }

    public RespostaAssistente(String resposta, String modo, List<ReferenciaDocumento> referencias, String agente,
                              String modelo, List<String> passos, Recuperacao recuperacao) {
        this(resposta, modo, referencias, agente, modelo, passos, recuperacao, List.of());
    }

    /** Mesma resposta com os Action Cards (Issue #136). */
    public RespostaAssistente comAcoes(List<AcaoSugerida> acoes) {
        return new RespostaAssistente(resposta, modo, referencias, agente, modelo, passos, recuperacao,
                acoes == null ? List.of() : List.copyOf(acoes));
    }

    /** Respostas sem agente (demonstrativa extrativa ou provider externo). */
    public RespostaAssistente(String resposta, String modo, List<ReferenciaDocumento> referencias) {
        this(resposta, modo, referencias, "Provider externo configurado".equals(modo) ? "EXTERNO" : "DEMONSTRATIVO",
                null, List.of());
    }

    /** Mesma resposta com o modo de recuperação informado. */
    public RespostaAssistente comRecuperacao(Recuperacao r) {
        return new RespostaAssistente(resposta, modo, referencias, agente, modelo, passos, r, acoesSugeridas);
    }
}
