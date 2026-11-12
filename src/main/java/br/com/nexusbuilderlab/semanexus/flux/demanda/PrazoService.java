package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaFiltro;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.PrazoDtos.EstadoPrazo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.PrazoDtos.MaisAntigaVencida;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.PrazoDtos.PrazoItem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.PrazoDtos.PrazoResumo;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Controle de prazos do Flux (Issue #84): resumo e listagem por estado, no escopo da listagem de demandas. */
@Service
public class PrazoService {

    static final int DIAS_ALERTA_PADRAO = 5;

    private final DemandaRepository demandas;
    private final EquipeRepository equipes;
    private final Clock clock;

    public PrazoService(DemandaRepository demandas, EquipeRepository equipes, Clock clock) {
        this.demandas = demandas;
        this.equipes = equipes;
        this.clock = clock;
    }

    /** Estado do prazo; {@code null} quando a demanda não tem prazo definido. */
    static EstadoPrazo estado(Demanda d, LocalDate hoje, int diasAlerta) {
        if (d.getPrazo() == null) {
            return null;
        }
        if (d.getStatus() == StatusDemanda.FINALIZADA) {
            return EstadoPrazo.CONCLUIDA;
        }
        if (d.getPrazo().isBefore(hoje)) {
            return EstadoPrazo.VENCIDA;
        }
        return d.getPrazo().isAfter(hoje.plusDays(diasAlerta)) ? EstadoPrazo.NO_PRAZO : EstadoPrazo.PROXIMA;
    }

    /** Finalizada dentro do prazo (conclusão sem data é tratada como no prazo; exige prazo definido). */
    static boolean concluidaNoPrazo(Demanda d) {
        return d.getConcluidaEm() == null || !d.getConcluidaEm().toLocalDate().isAfter(d.getPrazo());
    }

    @Transactional(readOnly = true)
    public PrazoResumo resumo(UsuarioAutenticado ator, Integer diasAlerta) {
        LocalDate hoje = hoje();
        int alerta = validar(diasAlerta);
        long vencidas = 0;
        long proximas = 0;
        long noPrazo = 0;
        long noPrazoConcl = 0;
        long atrasoConcl = 0;
        Demanda maisAntiga = null;
        for (Demanda d : carregar(ator, hoje)) {
            EstadoPrazo e = estado(d, hoje, alerta);
            if (e == null) {
                continue;
            }
            switch (e) {
                case VENCIDA -> {
                    vencidas++;
                    if (maisAntiga == null || d.getPrazo().isBefore(maisAntiga.getPrazo())) {
                        maisAntiga = d;
                    }
                }
                case PROXIMA -> proximas++;
                case NO_PRAZO -> noPrazo++;
                case CONCLUIDA -> {
                    if (concluidaNoPrazo(d)) {
                        noPrazoConcl++;
                    } else {
                        atrasoConcl++;
                    }
                }
            }
        }
        MaisAntigaVencida antiga = maisAntiga == null ? null : new MaisAntigaVencida(maisAntiga.getNumero(),
                ChronoUnit.DAYS.between(maisAntiga.getPrazo(), hoje));
        return new PrazoResumo(vencidas, proximas, noPrazo, noPrazoConcl, atrasoConcl, antiga, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<PrazoItem> listar(UsuarioAutenticado ator, EstadoPrazo estado, Integer diasAlerta,
                                          Pageable paginacao) {
        LocalDate hoje = hoje();
        int alerta = validar(diasAlerta);
        List<PrazoItem> todos = carregar(ator, hoje).stream()
                .filter(d -> {
                    EstadoPrazo e = estado(d, hoje, alerta);
                    return e != null && (estado == null || e == estado);
                })
                .sorted(Comparator.comparing(Demanda::getPrazo).thenComparing(Demanda::getId))
                .map(d -> item(d, hoje, alerta))
                .toList();
        int total = todos.size();
        int de = (int) Math.min(paginacao.getOffset(), total);
        int ate = Math.min(de + paginacao.getPageSize(), total);
        int paginas = (int) Math.ceil(total / (double) paginacao.getPageSize());
        return new PageResponse<>(todos.subList(de, ate), paginacao.getPageNumber(), paginacao.getPageSize(),
                total, paginas);
    }

    private PrazoItem item(Demanda d, LocalDate hoje, int alerta) {
        EstadoPrazo e = estado(d, hoje, alerta);
        Long atraso = null;
        Long restantes = null;
        if (e == EstadoPrazo.VENCIDA) {
            atraso = ChronoUnit.DAYS.between(d.getPrazo(), hoje);
        } else if (e == EstadoPrazo.CONCLUIDA) {
            if (!concluidaNoPrazo(d)) {
                atraso = ChronoUnit.DAYS.between(d.getPrazo(), d.getConcluidaEm().toLocalDate());
            }
        } else {
            restantes = ChronoUnit.DAYS.between(hoje, d.getPrazo());
        }
        return new PrazoItem(d.getId(), d.getNumero(), d.getTitulo(), d.getMunicipio().getNome(),
                d.getEquipe() == null ? null : d.getEquipe().getNome(),
                d.getFiscal() == null ? null : d.getFiscal().getNome(), d.getPrazo(),
                d.getPrioridade() == null ? null : d.getPrioridade().name(), d.getStatus().name(), e, atraso,
                restantes, true);
    }

    private int validar(Integer diasAlerta) {
        if (diasAlerta == null) {
            return DIAS_ALERTA_PADRAO;
        }
        if (diasAlerta < 0 || diasAlerta > 365) {
            throw new DadosInvalidosException("diasAlerta", "Informe um valor entre 0 e 365.");
        }
        return diasAlerta;
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(DemandaService.FUSO));
    }

    /** Demandas no escopo do perfil (FISCAL: só as suas ou da sua equipe), igual à listagem. */
    private List<Demanda> carregar(UsuarioAutenticado ator, LocalDate hoje) {
        boolean fiscal = ator.perfil() == Perfil.FISCAL;
        List<Long> equipesDoFiscal = fiscal ? equipes.findIdsByMembro(ator.id()) : List.of();
        DemandaFiltro semFiltro = new DemandaFiltro(null, null, null, null, null, null, null, null, null, null);
        return demandas.findAll(DemandaSpecification.listagem(semFiltro, hoje, equipesDoFiscal,
                fiscal ? ator.id() : null, Sort.unsorted()));
    }
}
