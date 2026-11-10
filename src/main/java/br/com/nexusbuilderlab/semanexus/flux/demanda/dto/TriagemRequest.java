package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Conclusão da triagem: classificar (→ Aguardando distribuição) ou solicitar complementação (→ Pendente). */
@Schema(description = "Triagem da demanda. Com decisao=CLASSIFICAR, categoria e prioridade são obrigatórias "
        + "(erro 400 por campo); com SOLICITAR_COMPLEMENTACAO só a justificativa é exigida.")
public record TriagemRequest(
        @NotNull DecisaoTriagem decisao,
        @Schema(description = "Obrigatória quando decisao=CLASSIFICAR", example = "DESMATAMENTO_ILEGAL", nullable = true)
        CategoriaFiscalizacao categoria,
        @Schema(description = "Obrigatória quando decisao=CLASSIFICAR", example = "ALTA", nullable = true)
        Prioridade prioridade,
        @Schema(description = "Justificativa da decisão", example = "Indícios compatíveis com a categoria; prioridade alta pelo tamanho da área.")
        @NotBlank @Size(max = 2000) String justificativa) {
}
