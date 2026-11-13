package br.com.nexusbuilderlab.semanexus.flux.agente;

import java.time.LocalDate;
import java.util.List;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DecisaoTriagem;

import io.swagger.v3.oas.annotations.media.Schema;

/** Sugestão do Agente de Triagem. Nada é gravado: o usuário revisa e confirma pelo {@code POST /triagem}. */
@Schema(description = "Sugestão do Agente de Triagem (IA local, Claude ou regras locais), sempre revisada por uma pessoa")
public record SugestaoTriagem(
        @Schema(description = "LOCAL (IA local), CLAUDE ou DEMONSTRATIVO (regras locais)", example = "LOCAL") String modo,
        @Schema(example = "qwen3:4b-instruct") String modelo,
        DecisaoTriagem decisao,
        @Schema(nullable = true) CategoriaFiscalizacao categoria,
        @Schema(nullable = true) Prioridade prioridade,
        @Schema(description = "Núcleo regional sugerido pela região do município", nullable = true) Nucleo nucleo,
        @Schema(description = "Texto pronto para a justificativa da triagem") String justificativa,
        @Schema(description = "Demandas abertas que podem tratar do mesmo fato") List<Duplicata> duplicatas,
        @Schema(description = "O que o agente consultou para chegar à sugestão") List<String> evidencias,
        @Schema(description = "Avisos (ex.: IA indisponível, campo completado pelas regras locais)") List<String> avisos) {

    @Schema(description = "Núcleo Regional de Fiscalização (fictício)")
    public record Nucleo(@Schema(example = "NR-SNO") String sigla, @Schema(example = "Núcleo Regional de Sinop") String nome) {
    }

    @Schema(description = "Possível duplicata")
    public record Duplicata(Long id, String numero, String titulo, StatusDemanda status, LocalDate dataRecebimento) {
    }
}
