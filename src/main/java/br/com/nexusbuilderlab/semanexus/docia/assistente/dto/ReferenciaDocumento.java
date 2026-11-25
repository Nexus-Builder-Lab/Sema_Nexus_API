package br.com.nexusbuilderlab.semanexus.docia.assistente.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Citação: documento de origem, trecho usado na resposta, a aderência textual do documento à pergunta e — desde a
 * Issue #141 — a similaridade semântica do trecho mais próximo (busca híbrida).
 *
 * @param aderenciaTextual      alta/média/baixa relativa ao documento com mais termos da consulta (busca textual). Não
 *                              é probabilidade de a resposta estar correta; nula quando o documento não veio da busca
 *                              textual (lido pelo agente ou encontrado só pela parte vetorial).
 * @param similaridadeSemantica cosseno (−1 a 1) entre a pergunta e o trecho mais próximo do documento no espaço do
 *                              modelo de embeddings. Proximidade de sentido, não confiança; nula sem busca vetorial.
 */
public record ReferenciaDocumento(
        Long documentoId,
        String titulo,
        String trecho,
        @Schema(description = "Aderência textual do documento à pergunta: alta (70%+), média (40–70%) ou baixa, "
                + "relativa ao documento com mais termos da consulta na busca textual. Não é probabilidade de a "
                + "resposta estar correta. Nula quando o documento não veio da busca textual.",
                nullable = true, allowableValues = {"alta", "média", "baixa"}, example = "alta")
        String aderenciaTextual,
        @Schema(description = "Similaridade semântica (cosseno, −1 a 1, normalmente 0–1) entre a pergunta e o trecho "
                + "mais próximo do documento, calculada com embeddings locais na busca híbrida (Issue #141). Mede "
                + "proximidade de sentido, NÃO é confiança nem probabilidade de a resposta estar correta. Nula quando "
                + "a recuperação foi só lexical ou a parte vetorial não trouxe o documento.",
                nullable = true, example = "0.712")
        Double similaridadeSemantica) {

    /** Citação sem aderência medida (documento lido fora da busca). */
    public ReferenciaDocumento(Long documentoId, String titulo, String trecho) {
        this(documentoId, titulo, trecho, null, null);
    }

    public ReferenciaDocumento(Long documentoId, String titulo, String trecho, String aderenciaTextual) {
        this(documentoId, titulo, trecho, aderenciaTextual, null);
    }
}
