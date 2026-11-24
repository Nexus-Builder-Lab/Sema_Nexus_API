package br.com.nexusbuilderlab.semanexus.flux.agente;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas somente leitura do Agente de Distribuição (Issue #130). Cada método roda na própria transação de leitura,
 * usa projeções/agregações JPQL (sem carregar entidades nem N+1) e devolve registros simples, usados pelas ferramentas
 * do agente e pelas {@link RegrasDistribuicao}. Nada é gravado.
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

        /** Fiscal elegível (mesma preferência das regras: perfil FISCAL quando a equipe tem algum). */
        public Optional<Membro> fiscal(Long usuarioId) {
            return fiscais().stream().filter(m -> m.id().equals(usuarioId)).findFirst();
        }
    }

    /** Equipes candidatas e se a busca foi ampliada para todos os núcleos (núcleo sem equipe utilizável). */
    public record Candidatas(List<EquipeCandidata> equipes, boolean ampliada) {

        public Optional<EquipeCandidata> equipe(Long id) {
            return equipes.stream().filter(e -> e.id().equals(id)).findFirst();
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

    private final EntityManager em;

    public ConsultasDistribuicao(EntityManager em) {
        this.em = em;
    }

    /**
     * Equipes ativas do núcleo regional (setor com a sigla) que têm ao menos um membro ativo. Sem sigla, ou sem equipe
     * utilizável no núcleo, devolve as equipes ativas de todos os setores ({@code ampliada} = true quando havia núcleo).
     * Duas consultas (equipes e membros), sem N+1. O endpoint de distribuição não restringe o GESTOR por setor, então
     * aqui também não.
     */
    public Candidatas equipesCandidatas(String siglaNucleo) {
        List<Object[]> linhas = em.createQuery("""
                select e.id, e.nome, s.sigla, s.nome from Equipe e join e.setor s
                where e.ativo = true order by e.nome, e.id""", Object[].class).getResultList();
        Map<Long, List<Membro>> membros = new HashMap<>();
        if (!linhas.isEmpty()) {
            em.createQuery("""
                    select e.id, u.id, u.nome, u.perfil from Equipe e join e.membros u
                    where e.ativo = true and u.ativo = true order by u.nome, u.id""", Object[].class)
                    .getResultList()
                    .forEach(l -> membros.computeIfAbsent((Long) l[0], k -> new ArrayList<>())
                            .add(new Membro((Long) l[1], (String) l[2], (Perfil) l[3])));
        }
        List<EquipeCandidata> todas = linhas.stream()
                .map(l -> new EquipeCandidata((Long) l[0], (String) l[1], (String) l[2], (String) l[3],
                        List.copyOf(membros.getOrDefault((Long) l[0], List.of()))))
                .filter(e -> !e.membros().isEmpty())
                .toList();
        if (siglaNucleo == null) {
            return new Candidatas(todas, false);
        }
        List<EquipeCandidata> doNucleo = todas.stream().filter(e -> siglaNucleo.equals(e.setorSigla())).toList();
        return doNucleo.isEmpty() ? new Candidatas(todas, true) : new Candidatas(doNucleo, false);
    }

    /** Carga de trabalho das equipes informadas e de seus fiscais (agregada no banco: COUNT/SUM por equipe e fiscal). */
    public Cargas cargas(Collection<Long> equipeIds, LocalDate hoje) {
        Map<Long, Carga> porEquipe = new HashMap<>();
        Map<Long, Carga> porFiscal = new HashMap<>();
        if (equipeIds.isEmpty()) {
            return new Cargas(porEquipe, porFiscal);
        }
        List<Object[]> linhas = em.createQuery("""
                select eq.id, f.id, count(d),
                       sum(case when d.prazo < :hoje then 1 else 0 end),
                       sum(case when d.prazo >= :hoje and d.prazo <= :limite then 1 else 0 end)
                from Demanda d join d.equipe eq left join d.fiscal f
                where eq.id in :equipes and d.status in :status
                group by eq.id, f.id""", Object[].class)
                .setParameter("hoje", hoje)
                .setParameter("limite", hoje.plusDays(DIAS_PROXIMO_VENCIMENTO))
                .setParameter("equipes", equipeIds)
                .setParameter("status", OCUPAM_EQUIPE)
                .getResultList();
        for (Object[] l : linhas) {
            Carga c = new Carga(numero(l[2]), numero(l[3]), numero(l[4]));
            porEquipe.merge((Long) l[0], c, Carga::somar);
            if (l[1] != null) {
                porFiscal.merge((Long) l[1], c, Carga::somar);
            }
        }
        return new Cargas(porEquipe, porFiscal);
    }

    /**
     * Vistorias em campo (Distribuída/Em andamento) no mesmo município ou, com {@code somenteMunicipio=false}, na mesma
     * região intermediária do IBGE. Projeção com ordem (mesmo município primeiro, depois prazo) e limite no banco.
     */
    public List<DemandaAtiva> ativasNaRegiao(Long excluirId, Integer municipioId, String regiao,
                                             boolean somenteMunicipio) {
        boolean regional = !somenteMunicipio && regiao != null;
        String local = regional ? "(m.id = :municipio or m.regiao = :regiao)" : "m.id = :municipio";
        TypedQuery<Object[]> q = em.createQuery("""
                select d.numero, d.titulo, d.status, m.nome, m.id, eq.id, eq.nome, f.nome, d.prazo
                from Demanda d join d.municipio m left join d.equipe eq left join d.fiscal f
                where %s and d.status in :status and d.id <> :excluir
                order by case when m.id = :municipio then 0 else 1 end, d.prazo asc nulls last, d.id"""
                .formatted(local), Object[].class)
                .setParameter("municipio", municipioId)
                .setParameter("status", EM_CAMPO)
                .setParameter("excluir", excluirId)
                .setMaxResults(MAX_ATIVAS_REGIAO);
        if (regional) {
            q.setParameter("regiao", regiao);
        }
        return q.getResultList().stream()
                .map(l -> new DemandaAtiva((String) l[0], (String) l[1], (StatusDemanda) l[2], (String) l[3],
                        municipioId.equals(l[4]), (Long) l[5], (String) l[6], (String) l[7], (LocalDate) l[8]))
                .toList();
    }

    /** Tempo médio histórico de conclusão da categoria (todas quando nula); projeção só das duas datas. */
    public TempoMedio tempoMedio(CategoriaFiscalizacao categoria) {
        TypedQuery<Object[]> q = em.createQuery("""
                select d.dataRecebimento, d.concluidaEm from Demanda d
                where d.status = :finalizada and d.concluidaEm is not null and d.dataRecebimento is not null"""
                + (categoria == null ? "" : " and d.categoria = :categoria"), Object[].class)
                .setParameter("finalizada", StatusDemanda.FINALIZADA);
        if (categoria != null) {
            q.setParameter("categoria", categoria);
        }
        List<Long> dias = q.getResultList().stream()
                .map(l -> Math.max(0, ChronoUnit.DAYS.between((LocalDate) l[0],
                        ((LocalDateTime) l[1]).toLocalDate())))
                .toList();
        Double media = dias.isEmpty() ? null
                : Math.round(dias.stream().mapToLong(Long::longValue).average().orElse(0) * 10) / 10.0;
        return new TempoMedio(categoria, dias.size(), media);
    }

    private static long numero(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
