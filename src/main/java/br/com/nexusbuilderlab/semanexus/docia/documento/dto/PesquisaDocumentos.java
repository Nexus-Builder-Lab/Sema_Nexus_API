package br.com.nexusbuilderlab.semanexus.docia.documento.dto;

import java.util.List;

import br.com.nexusbuilderlab.semanexus.docia.vetorial.Recuperacao;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado da pesquisa ranqueada da biblioteca (Issue #141), do mais ao menos relevante. */
@Schema(description = "Pesquisa ranqueada da biblioteca documental (dados fictícios na demonstração)")
public record PesquisaDocumentos(
        @Schema(description = "Consulta pesquisada (sem espaços nas pontas)", example = "área desmatada em Colniza")
        String consulta,
        @Schema(description = "Como os documentos foram recuperados: LEXICAL (só busca textual por termos/radicais) ou "
                + "HIBRIDA (busca textual + vetorial com embeddings locais, fundidas por RRF). Cai para LEXICAL, sem "
                + "erro, quando o modelo de embeddings não está disponível ou com modo=LEXICAL.", example = "HIBRIDA")
        Recuperacao recuperacao,
        @Schema(description = "Documentos do mais ao menos relevante; vazio quando nada atende à consulta (abstenção)")
        List<Item> resultados) {

    /** Documento encontrado com o trecho e as duas medidas, nenhuma delas de confiança. */
    public record Item(
            DocumentoResumo documento,
            @Schema(description = "Frase mais aderente aos termos ou, se o documento veio só da parte vetorial, o "
                    + "trecho mais próximo (até 400 caracteres)") String trecho,
            @Schema(description = "Aderência textual: alta (70%+), média (40–70%) ou baixa, relativa ao documento com "
                    + "mais termos da consulta. Não é probabilidade de acerto. Nula quando o documento não tem "
                    + "nenhum termo da consulta (veio só da parte vetorial).", nullable = true,
                    allowableValues = {"alta", "média", "baixa"}, example = "alta") String aderenciaTextual,
            @Schema(description = "Similaridade semântica (cosseno, −1 a 1) entre a consulta e o trecho mais próximo, "
                    + "com embeddings locais. Proximidade de sentido, NÃO é confiança. Nula na recuperação LEXICAL.",
                    nullable = true, example = "0.712") Double similaridadeSemantica) {
    }
}
