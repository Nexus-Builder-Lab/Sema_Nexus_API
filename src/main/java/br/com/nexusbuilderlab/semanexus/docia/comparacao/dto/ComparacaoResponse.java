package br.com.nexusbuilderlab.semanexus.docia.comparacao.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Resultado da comparação determinística de dois documentos (não é IA)")
public record ComparacaoResponse(
        String aviso,
        boolean geradoPorIa,
        String metodo,
        DocumentoRef a,
        DocumentoRef b,
        List<CampoMetadado> metadados,
        @Schema(description = "Similaridade do texto por palavras (0 a 100), 2*LCS/(nA+nB)") double similaridade,
        @Schema(description = "Similaridade por conjunto de termos (Jaccard, 0 a 100)") double similaridadeTermos,
        List<DiffLinha> diffLinhas,
        List<DiffTrecho> diffPalavras,
        List<TrechoSemelhante> trechosSemelhantes,
        List<String> termosExclusivosA,
        List<String> termosExclusivosB,
        @Schema(description = "true se algum texto foi truncado para o limite do cálculo") boolean truncado) {

    public record DocumentoRef(Long id, String titulo) {
    }

    public record CampoMetadado(String campo, String valorA, String valorB, boolean igual) {
    }

    @Schema(description = "tipo: IGUAL, REMOVIDO (só em A) ou ADICIONADO (só em B)")
    public record DiffLinha(String tipo, String texto) {
    }

    public record DiffTrecho(String tipo, String texto) {
    }

    public record TrechoSemelhante(String trechoA, String trechoB, double similaridade) {
    }
}
