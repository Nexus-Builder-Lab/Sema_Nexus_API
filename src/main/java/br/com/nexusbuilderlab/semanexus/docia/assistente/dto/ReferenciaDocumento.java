package br.com.nexusbuilderlab.semanexus.docia.assistente.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Citação: documento de origem, trecho usado na resposta e a aderência textual do documento à pergunta.
 *
 * @param aderenciaTextual alta/média/baixa relativa ao documento com mais termos da consulta (busca textual). Não é
 *                         probabilidade de a resposta estar correta; nula quando o documento não veio da busca.
 */
public record ReferenciaDocumento(
        Long documentoId,
        String titulo,
        String trecho,
        @Schema(description = "Aderência textual do documento à pergunta: alta (70%+), média (40–70%) ou baixa, "
                + "relativa ao documento com mais termos da consulta na busca textual. Não é probabilidade de a "
                + "resposta estar correta. Nula quando o documento não veio da busca.",
                nullable = true, allowableValues = {"alta", "média", "baixa"}, example = "alta")
        String aderenciaTextual) {

    /** Citação sem aderência medida (documento lido fora da busca). */
    public ReferenciaDocumento(Long documentoId, String titulo, String trecho) {
        this(documentoId, titulo, trecho, null);
    }
}
