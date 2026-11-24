package br.com.nexusbuilderlab.semanexus.flux.agente;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.flux.demanda.Demanda;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaRepository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas somente leitura do Agente de Distribuição (Issue #130). Cada método roda na própria transação de leitura e
 * devolve registros simples (sem entidades JPA), usados pelas ferramentas do agente e pelas {@link RegrasDistribuicao}.
 * Nada é gravado.
 */
@Service
@Transactional(readOnly = true)
public class ConsultasDistribuicao {

    /** Status em que a demanda ocupa a equipe/fiscal (já distribuída e não finalizada). */
    static final List<StatusDemanda> OCUPAM_EQUIPE = List.of(StatusDemanda.DISTRIBUIDA, StatusDemanda.EM_ANDAMENTO,
            StatusDemanda.AGUARDANDO_CONCLUSAO);
    /** Status de vistoria em campo (logística regional). */
    static final List<StatusDemanda> EM_CAMPO = List.of(StatusDemanda.DISTRIBUIDA, StatusDemanda.EM_ANDAMENTO);
    /** Janela (dias) de "prazo próximo do vencimento". */
    static final int DIAS_PROXIMO_VENCIMENTO = 7;
    static final int MAX_ATIVAS_REGIAO = 15;

    /** Membro ativo de uma equipe. */
    public record Membro(Long id, String nome, Perfil perfil) {
    }

    /** Equipe ativa com seus membros ativos. */
    public record EquipeCandidata(Long id, String nome, String setorSigla, String setorNome, List<Membro> membros) {

        /** Membros com perfil FISCAL; se não houver, todos os membros ativos (mesma regra do endpoint distribuir). */
        public List<Membro> fiscais() {
            List<Membro> f = membros.stream().filter(m -> m.perfil() == Perfil.FISCAL).toList();
            return f.isEmpty() ? membros : f;
        }

        public Optional<Membro> membro(Long usuarioId) {
            return membros.stream().filter(m -> m.id().equals(usuarioId)).findFirst();
        }
    }

    /** Carga de trabalho: demandas ativas, com prazo vencido e com prazo nos próximos 7 dias. */
    public record Carga(long ativas, long atrasadas, long proximasDoVencimento) {

        static final Carga ZERO = new Carga(0, 0, 0);

        Carga somar(Carga outra) {
            return new Carga(ativas + outra.ativas, atrasadas + outra.atrasadas,
                    proximasDoVencimento + outra.proximasDoVencimento);
        }
    }

    /** Cargas por equipe e por fiscal (chave: id). */
    public record Cargas(Map<Long, Carga> porEquipe, Map<Long, Carga> porFiscal) {

        public Carga equipe(Long id) {
            return porEquipe.getOrDefault(id, Carga.ZERO);
        }

        public Carga fiscal(Long id) {
            return porFiscal.getOrDefault(id, Carga.ZERO);
        }
    }

    /** Vistoria em andamento na região (logística de campo). */
    public record DemandaAtiva(String numero, String titulo, StatusDemanda status, String municipio,
                               boolean mesmoMunicipio, Long equipeId, String equipe, String fiscal, LocalDate prazo) {
    }

    /** Tempo médio de conclusão (dias entre recebimento e conclusão) das demandas finalizadas. */
    public record TempoMedio(CategoriaFiscalizacao categoria, long amostra, Double mediaDias) {
    }

    private final EquipeRepository equipes;
    private final DemandaRepository demandas;

    public ConsultasDistribuicao(EquipeRepository equipes, DemandaRepository demandas) {
        this.equipes = equipes;
        this.demandas = demandas;
    }

    /**
     * Equipes ativas do núcleo regional (setor com a sigla) que têm ao menos um membro ativo. Sem sigla, ou sem equipe
     * utilizável no núcleo, devolve as equipes ativas de todos os setores.
     */
    public List<EquipeCandidata> equipesCandidatas(String siglaNucleo) {
        List<EquipeCandidata> todas = equipes.findAllComSetorEResponsavel().stream()
                .filter(Equipe::isAtivo)
                .map(this::candidata)
                .filter(e -> !e.membros().isEmpty())
                .toList();
        if (siglaNucleo == null) {
            return todas;
        }
        List<EquipeCandidata> doNucleo = todas.stream().filter(e -> siglaNucleo.equals(e.setorSigla())).toList();
        return doNucleo.isEmpty() ? todas : doNucleo;
    }

    /** Equipe ativa pelo id (com membros ativos); vazio se não existe ou está inativa. */
    public Optional<EquipeCandidata> equipeAtiva(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return equipes.findById(id).filter(Equipe::isAtivo).map(this::candidata);
    }

    private EquipeCandidata candidata(Equipe e) {
        List<Membro> membros = equipes.findMembros(e.getId()).stream()
                .filter(Usuario::isAtivo)
                .map(u -> new Membro(u.getId(), u.getNome(), u.getPerfil()))
                .toList();
        return new EquipeCandidata(e.getId(), e.getNome(), e.getSetor() == null ? null : e.getSetor().getSigla(),
                e.getSetor() == null ? null : e.getSetor().getNome(), membros);
    }

    /** Carga de trabalho das equipes informadas e de seus fiscais (demandas distribuídas e não finalizadas). */
    public Cargas cargas(Collection<Long> equipeIds, LocalDate hoje) {
        Map<Long, Carga> porEquipe = new HashMap<>();
        Map<Long, Carga> porFiscal = new HashMap<>();
        if (equipeIds.isEmpty()) {
            return new Cargas(porEquipe, porFiscal);
        }
        Specification<Demanda> spec = (root, q, cb) -> cb.and(root.get("equipe").get("id").in(equipeIds),
                root.get("status").in(OCUPAM_EQUIPE));
        for (Demanda d : demandas.findAll(spec)) {
            LocalDate prazo = d.getPrazo();
            boolean atrasada = prazo != null && prazo.isBefore(hoje);
            boolean proxima = prazo != null && !prazo.isBefore(hoje)
                    && !prazo.isAfter(hoje.plusDays(DIAS_PROXIMO_VENCIMENTO));
            Carga c = new Carga(1, atrasada ? 1 : 0, proxima ? 1 : 0);
            porEquipe.merge(d.getEquipe().getId(), c, Carga::somar);
            if (d.getFiscal() != null) {
                porFiscal.merge(d.getFiscal().getId(), c, Carga::somar);
            }
        }
        return new Cargas(porEquipe, porFiscal);
    }

    /**
     * Vistorias em campo (Distribuída/Em andamento) no mesmo município ou, com {@code somenteMunicipio=false}, na mesma
     * região intermediária do IBGE. As do município vêm primeiro; no máximo 15.
     */
    public List<DemandaAtiva> ativasNaRegiao(Long excluirId, Integer municipioId, String regiao,
                                             boolean somenteMunicipio) {
        Specification<Demanda> spec = (root, q, cb) -> {
            var local = somenteMunicipio || regiao == null
                    ? cb.equal(root.get("municipio").get("id"), municipioId)
                    : cb.or(cb.equal(root.get("municipio").get("id"), municipioId),
                            cb.equal(root.get("municipio").get("regiao"), regiao));
            return cb.and(local, root.get("status").in(EM_CAMPO), cb.notEqual(root.get("id"), excluirId));
        };
        List<DemandaAtiva> r = new ArrayList<>();
        for (Demanda d : demandas.findAll(spec)) {
            r.add(new DemandaAtiva(d.getNumero(), d.getTitulo(), d.getStatus(), d.getMunicipio().getNome(),
                    d.getMunicipio().getId().equals(municipioId),
                    d.getEquipe() == null ? null : d.getEquipe().getId(),
                    d.getEquipe() == null ? null : d.getEquipe().getNome(),
                    d.getFiscal() == null ? null : d.getFiscal().getNome(), d.getPrazo()));
        }
        r.sort(Comparator.comparing((DemandaAtiva a) -> !a.mesmoMunicipio())
                .thenComparing(DemandaAtiva::prazo, Comparator.nullsLast(Comparator.naturalOrder())));
        return r.size() > MAX_ATIVAS_REGIAO ? List.copyOf(r.subList(0, MAX_ATIVAS_REGIAO)) : r;
    }

    /** Tempo médio histórico de conclusão da categoria (todas as categorias quando nula). */
    public TempoMedio tempoMedio(CategoriaFiscalizacao categoria) {
        Specification<Demanda> spec = (root, q, cb) -> categoria == null
                ? cb.equal(root.get("status"), StatusDemanda.FINALIZADA)
                : cb.and(cb.equal(root.get("status"), StatusDemanda.FINALIZADA),
                        cb.equal(root.get("categoria"), categoria));
        List<Long> dias = demandas.findAll(spec).stream()
                .filter(d -> d.getConcluidaEm() != null && d.getDataRecebimento() != null)
                .map(d -> Math.max(0, ChronoUnit.DAYS.between(d.getDataRecebimento(),
                        d.getConcluidaEm().toLocalDate())))
                .toList();
        Double media = dias.isEmpty() ? null
                : Math.round(dias.stream().mapToLong(Long::longValue).average().orElse(0) * 10) / 10.0;
        return new TempoMedio(categoria, dias.size(), media);
    }
}
