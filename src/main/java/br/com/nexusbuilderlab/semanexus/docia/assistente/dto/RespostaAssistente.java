package br.com.nexusbuilderlab.semanexus.docia.assistente.dto;

import java.util.List;

/** Resposta do assistente: texto, modo (identificação do provider) e referências citadas. */
public record RespostaAssistente(String resposta, String modo, List<ReferenciaDocumento> referencias) {
}
