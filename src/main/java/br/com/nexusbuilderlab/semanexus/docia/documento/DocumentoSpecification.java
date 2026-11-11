package br.com.nexusbuilderlab.semanexus.docia.documento;

import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoFiltro;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros combináveis e pesquisa textual da biblioteca documental (Criteria API, parâmetros vinculados).
 * A pesquisa ignora maiúsculas e acentos: {@code TRANSLATE(coluna, maiúsculas/acentuadas, minúsculas simples) LIKE :padrao},
 * com o termo normalizado do mesmo modo no Java. É busca textual, não semântica.
 */
public final class DocumentoSpecification {

    static final Set<String> CAMPOS_ORDENAVEIS = Set.of("titulo", "dataDocumento", "categoria", "criadoEm");

    private static final String MINUSCULAS_ACENTUADAS = "áàâãäéèêëíìîïóòôõöúùûüçñ";
    private static final String MINUSCULAS_SIMPLES = "aaaaaeeeeiiiiooooouuuucn";
    private static final String LETRAS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /** Origem do TRANSLATE: maiúsculas, acentuadas minúsculas e acentuadas maiúsculas. */
    private static final String DE = LETRAS + MINUSCULAS_ACENTUADAS + MINUSCULAS_ACENTUADAS.toUpperCase(Locale.ROOT);
    /** Destino: tudo em minúsculas e sem acento (não usa LOWER, que o Hibernate recusa em CLOB). */
    private static final String PARA = LETRAS.toLowerCase(Locale.ROOT) + MINUSCULAS_SIMPLES + MINUSCULAS_SIMPLES;

    private DocumentoSpecification() {
    }

    /** {@code idsRestritos} (opcional) limita aos ids da pesquisa textual e/ou da demanda. */
    static Specification<Documento> listagem(DocumentoFiltro f, Collection<Long> idsRestritos, Sort ordenacao) {
        List<Specification<Documento>> partes = new ArrayList<>();
        partes.add((r, q, cb) -> cb.isNull(r.get("excluidoEm")));
        partes.add(carregarAssociacoes());
        partes.add(igualIgnorandoCaixa("categoria", f.categoria()));
        partes.add(igualIgnorandoCaixa("tipo", f.tipo()));
        if (f.municipioId() != null) {
            partes.add((r, q, cb) -> cb.equal(r.get("municipio").get("id"), f.municipioId()));
        }
        if (idsRestritos != null) {
            partes.add(idsRestritos.isEmpty() ? (r, q, cb) -> cb.disjunction()
                    : (r, q, cb) -> r.get("id").in(idsRestritos));
        }
        if (f.de() != null) {
            partes.add((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("dataDocumento"), f.de()));
        }
        if (f.ate() != null) {
            partes.add((r, q, cb) -> cb.lessThanOrEqualTo(r.get("dataDocumento"), f.ate()));
        }
        partes.add(ordenadaPor(ordenacao));
        return Specification.allOf(partes);
    }

    /** Minúsculas e sem acentos (mesma transformação aplicada às colunas no banco). */
    public static String normalizar(String s) {
        String semAcento = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return Normalizer.normalize(semAcento, Normalizer.Form.NFC);
    }

    /**
     * Ids dos documentos ativos que contêm o termo (SQL parametrizado). O TRANSLATE é feito em SQL porque o
     * Hibernate recusa funções de texto sobre CLOB na Criteria API.
     */
    public static final String SQL_BUSCA = "SELECT ID FROM DOCUMENTO WHERE EXCLUIDO_EM IS NULL AND ("
            + coluna("TITULO") + " OR " + coluna("DESCRICAO") + " OR " + coluna("PALAVRAS_CHAVE") + " OR "
            + coluna("TEXTO_EXTRAIDO") + ")";

    private static String coluna(String nome) {
        return "TRANSLATE(" + nome + ", '" + DE + "', '" + PARA + "') LIKE ? ESCAPE '\\'";
    }

    /** Padrão LIKE já normalizado (minúsculas, sem acentos, curingas escapados). */
    public static String padraoBusca(String q) {
        return "%" + escaparLike(normalizar(q.strip())) + "%";
    }

    private static Specification<Documento> igualIgnorandoCaixa(String atributo, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String v = valor.strip().toLowerCase(Locale.ROOT);
        return (r, q, cb) -> cb.equal(cb.lower(r.get(atributo)), v);
    }

    private static Specification<Documento> carregarAssociacoes() {
        return (root, query, cb) -> {
            if (!contagem(query)) {
                root.fetch("municipio", JoinType.LEFT);
                root.fetch("responsavel", JoinType.INNER);
            }
            return null;
        };
    }

    private static Specification<Documento> ordenadaPor(Sort ordenacao) {
        return (root, query, cb) -> {
            if (!contagem(query)) {
                List<Order> ordens = new ArrayList<>();
                for (Sort.Order o : ordenacao) {
                    ordens.add(o.isAscending() ? cb.asc(root.get(o.getProperty())) : cb.desc(root.get(o.getProperty())));
                }
                ordens.add(cb.asc(root.get("id")));
                query.orderBy(ordens);
            }
            return null;
        };
    }

    private static boolean contagem(CriteriaQuery<?> query) {
        Class<?> tipo = query.getResultType();
        return Long.class.equals(tipo) || long.class.equals(tipo);
    }

    static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
