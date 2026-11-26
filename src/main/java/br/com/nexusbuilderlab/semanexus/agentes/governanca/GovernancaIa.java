package br.com.nexusbuilderlab.semanexus.agentes.governanca;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Estatísticas agregadas de governança de IA no período (Issue #131). Só contagens: nada pessoal. */
@Schema(description = "Governança de IA: concordância Humano × IA por agente e categoria e feedback do DocIA, nos "
        + "últimos `periodoDias` dias (pela data da decisão humana)")
public record GovernancaIa(
        @Schema(example = "30") int periodoDias,
        @Schema(description = "Concordância por agente (triagem, distribuicao)") List<Concordancia> porAgente,
        @Schema(description = "Concordância por agente e categoria da demanda") List<Concordancia> porCategoria,
        @Schema(description = "Feedback dos usuários nas respostas do Assistente DocIA") FeedbackDocia docia) {

    @Schema(description = "Decisões humanas após sugestão do agente")
    public record Concordancia(
            @Schema(example = "triagem") String agente,
            @Schema(description = "Categoria da demanda (nula no agregado por agente; SEM_CATEGORIA quando a demanda "
                    + "não tinha categoria)", nullable = true, example = "QUEIMADA_INCENDIO") String categoria,
            @Schema(description = "Decisões avaliadas no período") long avaliadas,
            long aceitaIntegral,
            long ajustadaParcial,
            long descartada,
            @Schema(description = "% aceita integralmente (0–100, 1 casa); nulo sem avaliações", nullable = true,
                    example = "62.5") Double concordanciaPct,
            @Schema(description = "% aceita integral ou parcialmente (0–100, 1 casa); nulo sem avaliações",
                    nullable = true, example = "87.5") Double aproveitamentoPct) {
    }

    @Schema(description = "Feedback opcional UTIL/IMPRECISA nas respostas do DocIA")
    public record FeedbackDocia(long util, long imprecisa,
                                @Schema(description = "% úteis (0–100, 1 casa); nulo sem feedback", nullable = true)
                                Double utilPct) {
    }
}
