package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acesso à tabela {@code DOCUMENTO_TRECHO} (V10, Issue #141), em SQL parametrizado. A busca usa
 * {@code VECTOR_DISTANCE(..., COSINE)} exata e devolve o trecho mais próximo de cada documento ativo.
 */
@Repository
public class TrechoRepositorio {

    /** Texto e campos de um documento ativo para a indexação. */
    public record DocumentoTexto(long id, String titulo, String descricao, String palavrasChave, String texto) {
    }

    /** Trecho mais próximo de um documento: distância de cosseno (0 = igual; similaridade = 1 − distância). */
    public record TrechoProximo(long documentoId, String texto, double distancia) {
    }

    static final String SQL_PROXIMOS = """
            SELECT DOCUMENTO_ID, TEXTO, DISTANCIA FROM (
                SELECT t.DOCUMENTO_ID, t.TEXTO,
                       VECTOR_DISTANCE(t.EMBEDDING, TO_VECTOR(?, 768, FLOAT32), COSINE) AS DISTANCIA,
                       ROW_NUMBER() OVER (PARTITION BY t.DOCUMENTO_ID
                           ORDER BY VECTOR_DISTANCE(t.EMBEDDING, TO_VECTOR(?, 768, FLOAT32), COSINE), t.ORDEM) AS RN
                FROM DOCUMENTO_TRECHO t JOIN DOCUMENTO d ON d.ID = t.DOCUMENTO_ID
                WHERE d.EXCLUIDO_EM IS NULL AND t.MODELO = ?%s
            ) WHERE RN = 1
            ORDER BY DISTANCIA, DOCUMENTO_ID
            FETCH FIRST ? ROWS ONLY""";

    static final String SQL_PENDENTES = """
            SELECT d.ID FROM DOCUMENTO d
            WHERE d.EXCLUIDO_EM IS NULL
              AND NOT EXISTS (SELECT 1 FROM DOCUMENTO_TRECHO t WHERE t.DOCUMENTO_ID = d.ID AND t.MODELO = ?)
            ORDER BY d.ID
            FETCH FIRST ? ROWS ONLY""";

    private final JdbcTemplate jdbc;

    public TrechoRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DocumentoTexto> documento(long id) {
        List<DocumentoTexto> r = jdbc.query("SELECT ID, TITULO, DESCRICAO, PALAVRAS_CHAVE, TEXTO_EXTRAIDO "
                        + "FROM DOCUMENTO WHERE ID = ? AND EXCLUIDO_EM IS NULL",
                (rs, i) -> new DocumentoTexto(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5)), id);
        return r.stream().findFirst();
    }

    /** Documentos ativos ainda sem trechos do modelo (idempotência do preenchimento ao iniciar). */
    public List<Long> pendentes(String modelo, int limite) {
        return jdbc.queryForList(SQL_PENDENTES, Long.class, modelo, limite);
    }

    public int contar(String modelo) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM DOCUMENTO_TRECHO WHERE MODELO = ?", Integer.class,
                modelo);
        return n == null ? 0 : n;
    }

    /** Substitui os trechos do documento para o modelo (apaga e insere na mesma transação). */
    @Transactional
    public void substituir(long documentoId, String modelo, List<String> textos, List<float[]> vetores) {
        jdbc.update("DELETE FROM DOCUMENTO_TRECHO WHERE DOCUMENTO_ID = ? AND MODELO = ?", documentoId, modelo);
        List<Object[]> linhas = new ArrayList<>(textos.size());
        for (int i = 0; i < textos.size(); i++) {
            linhas.add(new Object[] {documentoId, i, textos.get(i), clob(Vetores.literal(vetores.get(i))), modelo});
        }
        if (!linhas.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO DOCUMENTO_TRECHO (DOCUMENTO_ID, ORDEM, TEXTO, EMBEDDING, MODELO) "
                    + "VALUES (?, ?, ?, TO_VECTOR(?, 768, FLOAT32), ?)", linhas);
        }
    }

    /**
     * O literal do vetor tem ~10 mil caracteres: vai como CLOB (o driver recusaria um VARCHAR2 acima de 4.000 bytes)
     * e {@code TO_VECTOR} aceita CLOB.
     */
    private static SqlParameterValue clob(String literal) {
        return new SqlParameterValue(Types.CLOB, literal);
    }

    /** Limite de itens de uma lista IN do Oracle. */
    static final int MAX_IDS = 1000;

    /**
     * @param idsPermitidos não nulo: a busca percorre só esses documentos (filtro no SQL, ex.: os vinculados à
     *                      demanda); vazio devolve lista vazia
     */
    public List<TrechoProximo> maisProximos(float[] consulta, String modelo, int maxDocumentos,
                                            Set<Long> idsPermitidos) {
        if (idsPermitidos != null && idsPermitidos.isEmpty()) {
            return List.of();
        }
        SqlParameterValue v = clob(Vetores.literal(consulta));
        List<Object> args = new ArrayList<>(List.of(v, v, modelo));
        String filtro = "";
        if (idsPermitidos != null) {
            List<Long> ids = idsPermitidos.stream().sorted().limit(MAX_IDS).toList();
            filtro = " AND t.DOCUMENTO_ID IN (" + String.join(", ", java.util.Collections.nCopies(ids.size(), "?"))
                    + ")";
            args.addAll(ids);
        }
        args.add(maxDocumentos);
        return jdbc.query(SQL_PROXIMOS.formatted(filtro), (rs, i) -> new TrechoProximo(rs.getLong(1),
                rs.getString(2), rs.getDouble(3)), args.toArray());
    }
}
