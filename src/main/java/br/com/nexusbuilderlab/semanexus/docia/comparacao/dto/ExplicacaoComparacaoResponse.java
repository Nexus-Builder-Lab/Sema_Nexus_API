package br.com.nexusbuilderlab.semanexus.docia.comparacao.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Grandezas que mudaram entre duas versões (tabela determinística) e resumo curto do que mudou")
public record ExplicacaoComparacaoResponse(
        String aviso,
        @Schema(description = "Modo do resumo: LOCAL (IA local), CLAUDE ou DEMONSTRATIVO (regras, sem IA)")
        String modo,
        @Schema(description = "true quando o resumo foi escrito por modelo; a tabela nunca vem do modelo")
        boolean resumoGeradoPorIa,
        @Schema(description = "Modelo usado (nulo no modo demonstrativo)") String modelo,
        String resumo,
        ComparacaoResponse.DocumentoRef a,
        ComparacaoResponse.DocumentoRef b,
        @Schema(description = "Similaridade textual (%) calculada pela comparação determinística")
        double similaridade,
        List<Grandeza> grandezas,
        @Schema(description = "Quantidade de grandezas idênticas nas duas versões (não listadas)")
        int grandezasIguais,
        List<String> passos) {

    @Schema(description = "categoria: AREA, VAZAO, VALOR, DATA, PRAZO, PERCENTUAL ou CONCLUSAO; "
            + "situacao: ALTERADO, REMOVIDO (só em A) ou ADICIONADO (só em B)")
    public record Grandeza(String categoria, String situacao, String rotuloA, String valorA, String rotuloB,
                           String valorB, Double variacaoAbsoluta, Double variacaoPercentual, String unidade) {
    }
}
