package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaFiltro;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ProdutividadeResposta;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ProdutividadeResposta.Kpis;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ProdutividadeResposta.MesHistorico;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ProdutividadeResposta.Responsavel;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ProdutividadeResposta.SetorSituacao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ProdutividadeResposta.SetorTempoMedio;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores de produtividade do Flux (Issue #84). Recorte: demandas com data de recebimento em {@code [de, ate]}
 * (opcional) e, se informado, do setor da equipe designada; escopo do perfil igual ao da listagem de demandas.
 * "Setor" da demanda é o setor da equipe designada; "responsável" é o fiscal designado.
 */
@Service
public class ProdutividadeService {

    private final DemandaRepository demandas;
    private final EquipeRepository equipes;
    private final Clock clock;

    public ProdutividadeService(DemandaRepository demandas, EquipeRepository equipes, Clock clock) {
        this.demandas = demandas;
        this.equipes = equipes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProdutividadeResposta calcular(UsuarioAutenticado ator, LocalDate de, LocalDate ate, Long setorId) {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new DadosInvalidosException("de", "A data inicial não pode ser posterior à data final.");
        }
        LocalDate hoje = LocalDate.now(clock.withZone(DemandaService.FUSO));
        boolean fiscal = ator.perfil() == Perfil.FISCAL;
        List<Long> equipesDoFiscal = fiscal ? equipes.findIdsByMembro(ator.id()) : List.of();
        DemandaFiltro filtro = new DemandaFiltro(null, null, null, null, null, null, null, null, de, ate);
        List<Demanda> base = demandas.findAll(DemandaSpecification.listagem(filtro, hoje, equipesDoFiscal,
                fiscal ? ator.id() : null, Sort.unsorted()));
        if (setorId != null) {
            base = base.stream().filter(d -> d.getEquipe() != null && d.getEquipe().getSetor() != null
                    && setorId.equals(d.getEquipe().getSetor().getId())).toList();
        }
        return calcular(base, hoje);
    }

    /** Cálculo puro sobre o recorte já filtrado (testável sem banco). */
    static ProdutividadeResposta calcular(List<Demanda> base, LocalDate hoje) {
        long total = base.size();
        List<Demanda> concluidas = base.stream().filter(ProdutividadeService::finalizada).toList();
        long atrasadasAtivas = base.stream().filter(d -> d.atrasada(hoje)).count();
        List<Demanda> comPrazo = concluidas.stream().filter(d -> d.getPrazo() != null).toList();
        BigDecimal pctNoPrazo = comPrazo.isEmpty() ? null : percentual(
                comPrazo.stream().filter(PrazoService::concluidaNoPrazo).count(), comPrazo.size());
        Kpis kpis = new Kpis(total, concluidas.size(), total == 0 ? null : percentual(concluidas.size(), total),
                tempoMedio(concluidas), pctNoPrazo, atrasadasAtivas);
        return new ProdutividadeResposta(kpis, porSetor(base, hoje), tempoPorSetor(base), historico(base),
                porResponsavel(base, hoje), true);
    }

    static boolean finalizada(Demanda d) {
        return d.getStatus() == StatusDemanda.FINALIZADA;
    }

    /** Percentual 0-100 com 1 casa decimal. */
    static BigDecimal percentual(long parte, long todo) {
        return BigDecimal.valueOf(parte * 100.0 / todo).setScale(1, RoundingMode.HALF_UP);
    }

    /** Média de dias entre o recebimento e a conclusão (só finalizadas com data de conclusão); null se não houver. */
    static BigDecimal tempoMedio(List<Demanda> concluidas) {
        List<Long> dias = concluidas.stream().filter(d -> d.getConcluidaEm() != null && d.getDataRecebimento() != null)
                .map(d -> ChronoUnit.DAYS.between(d.getDataRecebimento(), d.getConcluidaEm().toLocalDate())).toList();
        if (dias.isEmpty()) {
            return null;
        }
        double media = dias.stream().mapToLong(Long::longValue).average().orElse(0);
        return BigDecimal.valueOf(media).setScale(1, RoundingMode.HALF_UP);
    }

    private static Setor setorDe(Demanda d) {
        return d.getEquipe() == null ? null : d.getEquipe().getSetor();
    }

    private static List<SetorSituacao> porSetor(List<Demanda> base, LocalDate hoje) {
        Map<Long, List<Demanda>> grupos = new LinkedHashMap<>();
        base.forEach(d -> grupos.computeIfAbsent(setorDe(d) == null ? null : setorDe(d).getId(),
                k -> new ArrayList<>()).add(d));
        List<SetorSituacao> r = new ArrayList<>();
        grupos.forEach((id, ds) -> {
            long conc = ds.stream().filter(ProdutividadeService::finalizada).count();
            r.add(new SetorSituacao(id, nomeSetor(ds.get(0)), ds.size(), conc, ds.size() - conc,
                    ds.stream().filter(d -> d.atrasada(hoje)).count()));
        });
        r.sort(Comparator.comparing(SetorSituacao::setor));
        return r;
    }

    private static List<SetorTempoMedio> tempoPorSetor(List<Demanda> base) {
        Map<Long, List<Demanda>> grupos = new LinkedHashMap<>();
        base.stream().filter(ProdutividadeService::finalizada).forEach(d -> grupos
                .computeIfAbsent(setorDe(d) == null ? null : setorDe(d).getId(), k -> new ArrayList<>()).add(d));
        List<SetorTempoMedio> r = new ArrayList<>();
        grupos.forEach((id, ds) -> {
            BigDecimal media = tempoMedio(ds);
            if (media != null) {
                r.add(new SetorTempoMedio(id, nomeSetor(ds.get(0)), media));
            }
        });
        r.sort(Comparator.comparing(SetorTempoMedio::setor));
        return r;
    }

    private static String nomeSetor(Demanda d) {
        return setorDe(d) == null ? "Sem setor" : setorDe(d).getNome();
    }

    /** Recebidas (mês de recebimento) × concluídas (mês de conclusão), meses contínuos do primeiro ao último. */
    private static List<MesHistorico> historico(List<Demanda> base) {
        TreeMap<YearMonth, long[]> meses = new TreeMap<>();
        for (Demanda d : base) {
            if (d.getDataRecebimento() != null) {
                meses.computeIfAbsent(YearMonth.from(d.getDataRecebimento()), k -> new long[2])[0]++;
            }
            if (finalizada(d) && d.getConcluidaEm() != null) {
                meses.computeIfAbsent(YearMonth.from(d.getConcluidaEm()), k -> new long[2])[1]++;
            }
        }
        List<MesHistorico> r = new ArrayList<>();
        if (meses.isEmpty()) {
            return r;
        }
        YearMonth ultimo = meses.lastKey();
        for (YearMonth m = meses.firstKey(); !m.isAfter(ultimo); m = m.plusMonths(1)) {
            long[] v = meses.getOrDefault(m, new long[2]);
            r.add(new MesHistorico(m.toString(), v[0], v[1]));
        }
        return r;
    }

    private static List<Responsavel> porResponsavel(List<Demanda> base, LocalDate hoje) {
        Map<Long, List<Demanda>> grupos = new LinkedHashMap<>();
        base.stream().filter(d -> d.getFiscal() != null)
                .forEach(d -> grupos.computeIfAbsent(d.getFiscal().getId(), k -> new ArrayList<>()).add(d));
        List<Responsavel> r = new ArrayList<>();
        grupos.forEach((id, ds) -> {
            Usuario f = ds.get(0).getFiscal();
            List<Demanda> conc = ds.stream().filter(ProdutividadeService::finalizada).toList();
            r.add(new Responsavel(id, f.getNome(), ds.size(), conc.size(), ds.size() - conc.size(),
                    ds.stream().filter(d -> d.atrasada(hoje)).count(), tempoMedio(conc),
                    percentual(conc.size(), ds.size())));
        });
        r.sort(Comparator.comparing(Responsavel::fiscal).thenComparing(Responsavel::fiscalId));
        return r;
    }
}
