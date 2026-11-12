package br.com.nexusbuilderlab.semanexus.intelligence.indicador;

import br.com.nexusbuilderlab.semanexus.flux.demanda.Demanda;
import br.com.nexusbuilderlab.semanexus.flux.demanda.Fiscalizacao;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorFiltro;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Consultas do Intelligence sobre as demandas (Criteria API: parâmetros vinculados, sem SQL concatenado).
 * Demandas excluídas logicamente já são filtradas pela própria entidade.
 */
@Repository
public class IndicadorRepository {

    @PersistenceContext
    private EntityManager em;

    /** Demandas que atendem aos filtros, com o município já carregado. */
    public List<Demanda> demandas(IndicadorFiltro filtro) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Demanda> q = cb.createQuery(Demanda.class);
        Root<Demanda> d = q.from(Demanda.class);
        d.fetch("municipio");
        Join<Demanda, ?> m = d.join("municipio");
        q.select(d).where(predicados(cb, d, m, filtro).toArray(Predicate[]::new));
        q.orderBy(cb.asc(d.get("id")));
        return em.createQuery(q).getResultList();
    }

    /** Página de demandas para relatórios (mais recentes primeiro), com o município já carregado. */
    public List<Demanda> demandasPagina(IndicadorFiltro filtro, int offset, int limite) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Demanda> q = cb.createQuery(Demanda.class);
        Root<Demanda> d = q.from(Demanda.class);
        d.fetch("municipio");
        Join<Demanda, ?> m = d.join("municipio");
        q.select(d).where(predicados(cb, d, m, filtro).toArray(Predicate[]::new));
        q.orderBy(cb.desc(d.get("dataRecebimento")), cb.desc(d.get("id")));
        return em.createQuery(q).setFirstResult(offset).setMaxResults(limite).getResultList();
    }

    /** Total de demandas que atendem aos filtros. */
    public long contarDemandas(IndicadorFiltro filtro) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Demanda> d = q.from(Demanda.class);
        Join<Demanda, ?> m = d.join("municipio");
        q.select(cb.count(d)).where(predicados(cb, d, m, filtro).toArray(Predicate[]::new));
        return em.createQuery(q).getSingleResult();
    }

    /** Fiscalizações registradas nas demandas que atendem aos filtros. */
    public long contarFiscalizacoes(IndicadorFiltro filtro) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Fiscalizacao> f = q.from(Fiscalizacao.class);
        Join<Fiscalizacao, Demanda> d = f.join("demanda");
        Join<Demanda, ?> m = d.join("municipio");
        q.select(cb.count(f)).where(predicados(cb, d, m, filtro).toArray(Predicate[]::new));
        return em.createQuery(q).getSingleResult();
    }

    private static List<Predicate> predicados(CriteriaBuilder cb, From<?, ? extends Demanda> d, From<?, ?> m,
                                              IndicadorFiltro f) {
        List<Predicate> p = new ArrayList<>();
        if (f == null) {
            return p;
        }
        if (f.de() != null) {
            p.add(cb.greaterThanOrEqualTo(d.get("dataRecebimento"), f.de()));
        }
        if (f.ate() != null) {
            p.add(cb.lessThanOrEqualTo(d.get("dataRecebimento"), f.ate()));
        }
        if (f.municipioId() != null) {
            p.add(cb.equal(m.get("id"), f.municipioId()));
        }
        if (f.bioma() != null) {
            p.add(cb.equal(m.get("bioma"), f.bioma()));
        }
        if (f.categoria() != null) {
            p.add(cb.equal(d.get("categoria"), f.categoria()));
        }
        if (f.status() != null) {
            p.add(cb.equal(d.get("status"), f.status()));
        }
        return p;
    }
}
