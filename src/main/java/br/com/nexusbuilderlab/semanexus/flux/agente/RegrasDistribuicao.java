package br.com.nexusbuilderlab.semanexus.flux.agente;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.Carga;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.Cargas;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.EquipeCandidata;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.Membro;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.TempoMedio;

/**
 * Regras locais do Agente de Distribuição (modo demonstrativo, sem IA generativa; Issue #130). Determinísticas: mesma
 * entrada, mesma sugestão.
 * <ul>
 *   <li>Fiscal: menor pontuação de carga ({@code ativas + 2×próximas do vencimento + 3×atrasadas}), com 1 ponto de
 *   desconto quando a equipe já tem vistoria em campo no mesmo município (logística). Empate: menor carga da equipe,
 *   nome da equipe, nome do fiscal.</li>
 *   <li>Prazo: hoje + tempo médio histórico da categoria (15 dias sem histórico), limitado a 7 dias para URGENTE e
 *   15 para ALTA, entre {@value #PRAZO_MINIMO_DIAS} e {@value #PRAZO_MAXIMO_DIAS} dias. Sempre ≥ hoje.</li>
 * </ul>
 */
final class RegrasDistribuicao {

    static final int PRAZO_PADRAO_DIAS = 15;
    static final int PRAZO_MINIMO_DIAS = 3;
    static final int PRAZO_MAXIMO_DIAS = 90;
    /** Prazo máximo aceito numa sugestão da IA (além disso não é "realista" para uma vistoria). */
    static final int PRAZO_MAXIMO_IA_DIAS = 180;

    /** Equipe e fiscal escolhidos, com a pontuação usada. */
    record Escolha(EquipeCandidata equipe, Membro fiscal, Carga cargaFiscal, long pontos, boolean mesmoMunicipio) {
    }

    private RegrasDistribuicao() {
    }

    static long pontos(Carga c) {
        return c.ativas() + 2 * c.proximasDoVencimento() + 3 * c.atrasadas();
    }

    /** Melhor par equipe/fiscal; nulo se não há equipe ativa com membro ativo. */
    static Escolha escolher(List<EquipeCandidata> equipes, Cargas cargas, Set<Long> equipesNoMunicipio) {
        Escolha melhor = null;
        Comparator<Escolha> ordem = Comparator.comparingLong(Escolha::pontos)
                .thenComparingLong(e -> pontos(cargas.equipe(e.equipe().id())))
                .thenComparing(e -> e.equipe().nome())
                .thenComparing(e -> e.fiscal().nome())
                .thenComparing(e -> e.fiscal().id());
        for (EquipeCandidata eq : equipes) {
            boolean local = equipesNoMunicipio.contains(eq.id());
            for (Membro m : eq.fiscais()) {
                Carga c = cargas.fiscal(m.id());
                Escolha e = new Escolha(eq, m, c, Math.max(0, pontos(c) - (local ? 1 : 0)), local);
                if (melhor == null || ordem.compare(e, melhor) < 0) {
                    melhor = e;
                }
            }
        }
        return melhor;
    }

    /** Prazo realista pela média histórica da categoria e pela prioridade; nunca antes de hoje. */
    static LocalDate prazo(LocalDate hoje, TempoMedio tempo, Prioridade prioridade) {
        long dias = tempo == null || tempo.mediaDias() == null ? PRAZO_PADRAO_DIAS
                : (long) Math.ceil(tempo.mediaDias());
        if (prioridade == Prioridade.URGENTE) {
            dias = Math.min(dias, 7);
        } else if (prioridade == Prioridade.ALTA) {
            dias = Math.min(dias, 15);
        }
        dias = Math.max(PRAZO_MINIMO_DIAS, Math.min(dias, PRAZO_MAXIMO_DIAS));
        return hoje.plusDays(dias);
    }
}
