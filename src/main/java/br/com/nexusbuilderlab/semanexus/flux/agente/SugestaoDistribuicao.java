package br.com.nexusbuilderlab.semanexus.flux.agente;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sugestão do Agente de Distribuição (Issue #130). Nada é gravado: o usuário revisa e aplica pelo
 * {@code POST /api/demandas/{id}/distribuicao}.
 */
@Schema(description = "Sugestão do Agente de Distribuição (IA local, Claude ou regras locais), sempre revisada por uma "
        + "pessoa e aplicada pelo endpoint de distribuição")
public record SugestaoDistribuicao(
        @Schema(description = "LOCAL (IA local), CLAUDE ou DEMONSTRATIVO (regras locais)", example = "LOCAL") String modo,
        @Schema(example = "qwen3:4b-instruct", nullable = true) String modelo,
        @Schema(description = "Equipe ativa sugerida (nula se não houver equipe ativa com membros)", nullable = true)
        EquipeSugerida equipe,
        @Schema(description = "Fiscal sugerido, membro ativo da equipe", nullable = true) FiscalSugerido fiscal,
        @Schema(description = "Prazo sugerido (>= hoje)", nullable = true, example = "2026-12-09") LocalDate prazo,
        @Schema(description = "Por que esta equipe, este fiscal e este prazo") String justificativa,
        @Schema(description = "O que o agente consultou para chegar à sugestão") List<String> passos,
        @Schema(description = "Avisos (ex.: IA indisponível, sugestão da IA descartada na validação)") List<String> avisos) {

    @Schema(description = "Equipe sugerida")
    public record EquipeSugerida(@Schema(example = "3") Long id, @Schema(example = "Equipe de Campo NR Sinop") String nome,
                                 @Schema(example = "NR-SNO", nullable = true) String setorSigla) {
    }

    @Schema(description = "Fiscal sugerido e sua carga atual")
    public record FiscalSugerido(@Schema(example = "12") Long id, String nome,
                                 @Schema(description = "Demandas distribuídas e não finalizadas") long ativas,
                                 @Schema(description = "Com prazo vencido") long atrasadas,
                                 @Schema(description = "Com prazo nos próximos 7 dias") long proximasDoVencimento) {
    }
}
