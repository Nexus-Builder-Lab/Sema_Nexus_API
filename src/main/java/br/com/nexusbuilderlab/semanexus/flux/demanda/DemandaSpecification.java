package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaFiltro;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros combináveis, escopo do perfil FISCAL, carga das associações e ordenação da listagem de demandas.
 * Tudo é montado com Criteria API (parâmetros vinculados, sem concatenação de SQL).
 */
final class DemandaSpecification {

    /** Campos aceitos em {@code sort=campo,asc|desc}. */
    static final Set<String> CAMPOS_ORDENAVEIS =
            Set.of("numero", "titulo", "dataRecebimento", "prazo", "status", "prioridade");

    private DemandaSpecification() {
    }

    /** Combina filtros, escopo (somente FISCAL), associações e ordenação. */
    static Specification<Demanda> listagem(DemandaFiltro filtro, LocalDate hoje, Collection<Long> equipesDoFiscal,
                                           Long fiscalId, Sort ordenacao) {
        List<Specification<Demanda>> partes = new ArrayList<>();
        partes.add(carregarAssociacoes());
        partes.add(texto(filtro.q()));
        partes.add(igual("status", filtro.status()));
        partes.add(igual("categoria", filtro.categoria()));
        partes.add(igual("prioridade", filtro.prioridade()));
        if (filtro.municipioId() != null) {
            partes.add((r, q, cb) -> cb.equal(r.get("municipio").get("id"), filtro.municipioId()));
        }
        if (filtro.equipeId() != null) {
            partes.add((r, q, cb) -> cb.equal(r.get("equipe").get("id"), filtro.equipeId()));
        }
        if (filtro.fiscalId() != null) {
            partes.add((r, q, cb) -> cb.equal(r.get("fiscal").get("id"), filtro.fiscalId()));
        }
        if (filtro.de() != null) {
            partes.add((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("dataRecebimento"), filtro.de()));
        }
        if (filtro.ate() != null) {
            partes.add((r, q, cb) -> cb.lessThanOrEqualTo(r.get("dataRecebimento"), filtro.ate()));
        }
        if (filtro.atrasada() != null) {
            partes.add(atrasada(filtro.atrasada(), hoje));
        }
        if (fiscalId != null) {
            partes.add(escopoDoFiscal(fiscalId, equipesDoFiscal));
        }
        partes.add(ordenadaPor(ordenacao));
        return Specification.allOf(partes);
    }

    /** Município, equipe e fiscal já carregados na consulta de dados (não na contagem), evitando N+1. */
    private static Specification<Demanda> carregarAssociacoes() {
        return (root, query, cb) -> {
            if (!consultaDeContagem(query)) {
                root.fetch("municipio", JoinType.INNER);
                root.fetch("equipe", JoinType.LEFT);
                root.fetch("fiscal", JoinType.LEFT);
            }
            return null;
        };
    }

    /** Número (DEM-2026/0010) ou parte do título, sem diferenciar maiúsculas de minúsculas. */
    private static Specification<Demanda> texto(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String padrao = "%" + escaparLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("numero")), padrao, '\\'),
                cb.like(cb.lower(root.get("titulo")), padrao, '\\'));
    }

    private static Specification<Demanda> igual(String atributo, Object valor) {
        return valor == null ? null : (root, query, cb) -> cb.equal(root.get(atributo), valor);
    }

    /** Atrasada: prazo vencido e não finalizada. {@code false} = demais (sem prazo, no prazo ou finalizadas). */
    static Specification<Demanda> atrasada(boolean atrasada, LocalDate hoje) {
        return (root, query, cb) -> {
            if (atrasada) {
                return cb.and(
                        cb.isNotNull(root.get("prazo")),
                        cb.lessThan(root.<LocalDate>get("prazo"), hoje),
                        cb.notEqual(root.get("status"), StatusDemanda.FINALIZADA));
            }
            return cb.or(
                    cb.isNull(root.get("prazo")),
                    cb.greaterThanOrEqualTo(root.<LocalDate>get("prazo"), hoje),
                    cb.equal(root.get("status"), StatusDemanda.FINALIZADA));
        };
    }

    /** FISCAL só enxerga as demandas atribuídas a ele ou designadas a uma equipe da qual é membro. */
    static Specification<Demanda> escopoDoFiscal(Long fiscalId, Collection<Long> equipeIds) {
        return (root, query, cb) -> {
            Predicate atribuida = cb.equal(root.get("fiscal").get("id"), fiscalId);
            if (equipeIds == null || equipeIds.isEmpty()) {
                return atribuida;
            }
            return cb.or(atribuida, root.get("equipe").get("id").in(equipeIds));
        };
    }

    /** Ordenação aplicada à consulta de dados; status e prioridade seguem a ordem do fluxo, não a alfabética. */
    private static Specification<Demanda> ordenadaPor(Sort ordenacao) {
        return (root, query, cb) -> {
            if (!consultaDeContagem(query)) {
                query.orderBy(ordens(root, cb, ordenacao));
            }
            return null;
        };
    }

    private static List<Order> ordens(Root<Demanda> root, CriteriaBuilder cb, Sort ordenacao) {
        List<Order> ordens = new ArrayList<>();
        for (Sort.Order o : ordenacao) {
            Expression<?> expressao = switch (o.getProperty()) {
                case "status" -> ordemDoEnum(root.get("status"), cb, StatusDemanda.values());
                case "prioridade" -> ordemDoEnum(root.get("prioridade"), cb,
                        Prioridade.values());
                default -> root.get(o.getProperty());
            };
            ordens.add(o.isAscending() ? cb.asc(expressao) : cb.desc(expressao));
        }
        ordens.add(cb.asc(root.get("id")));
        return ordens;
    }

    private static <E extends Enum<E>> Expression<Integer> ordemDoEnum(Path<E> caminho, CriteriaBuilder cb, E[] valores) {
        CriteriaBuilder.Case<Integer> caso = cb.selectCase();
        for (E valor : valores) {
            caso = caso.when(cb.equal(caminho, valor), valor.ordinal());
        }
        return caso.otherwise(-1);
    }

    private static boolean consultaDeContagem(CriteriaQuery<?> query) {
        Class<?> tipo = query.getResultType();
        return Long.class.equals(tipo) || long.class.equals(tipo);
    }

    static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
