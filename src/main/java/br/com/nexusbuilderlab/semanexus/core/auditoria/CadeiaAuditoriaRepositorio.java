package br.com.nexusbuilderlab.semanexus.core.auditoria;

import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoria.Registro;
import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoria.Selado;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acesso JDBC à cadeia de auditoria (Issue #148). A selagem atualiza colunas que o JPA trata como imutáveis, por isso
 * fica fora do {@link AuditoriaRepository}. Todo SQL é parametrizado.
 */
@Repository
public class CadeiaAuditoriaRepositorio {

    /** Estado da cadeia: registros com {@code ID <= inicioId} são legados (anteriores à V14, sem hash). */
    public record Estado(long inicioId, long ultimaSeq, String ultimoHash) {
    }

    private static final String COLUNAS =
            "ID, DATA, USUARIO_ID, ACAO, ENTIDADE, ENTIDADE_ID, DETALHE, VALOR_ANTERIOR, VALOR_NOVO";

    private final JdbcTemplate jdbc;

    public CadeiaAuditoriaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Lê o estado e trava a linha até o fim da transação: serializa a selagem entre threads e instâncias. */
    public Estado travarEstado() {
        return jdbc.queryForObject(
                "SELECT INICIO_ID, ULTIMA_SEQ, ULTIMO_HASH FROM AUDITORIA_CADEIA WHERE ID = 1 FOR UPDATE",
                (rs, i) -> new Estado(rs.getLong(1), rs.getLong(2), rs.getString(3)));
    }

    /** Estado sem trava (verificação). */
    public Estado estado() {
        return jdbc.queryForObject("SELECT INICIO_ID, ULTIMA_SEQ, ULTIMO_HASH FROM AUDITORIA_CADEIA WHERE ID = 1",
                (rs, i) -> new Estado(rs.getLong(1), rs.getLong(2), rs.getString(3)));
    }

    /** Registros ainda não selados posteriores ao início da cadeia, em ordem de ID. */
    public List<Registro> pendentes(long inicioId, int limite) {
        return jdbc.query("SELECT " + COLUNAS + " FROM AUDITORIA"
                        + " WHERE (CASE WHEN HASH_REGISTRO IS NULL THEN ID END) > ?"
                        + " ORDER BY ID FETCH FIRST ? ROWS ONLY",
                (rs, i) -> registro(rs), inicioId, limite);
    }

    /** Grava a selagem de um registro; devolve se o registro ainda estava pendente. */
    public boolean selar(long id, long seq, String hashAnterior, String hashRegistro) {
        return jdbc.update("UPDATE AUDITORIA SET SEQ_CADEIA = ?, HASH_ANTERIOR = ?, HASH_REGISTRO = ?"
                + " WHERE ID = ? AND HASH_REGISTRO IS NULL", seq, hashAnterior, hashRegistro, id) == 1;
    }

    public void atualizarEstado(long ultimaSeq, String ultimoHash) {
        jdbc.update("UPDATE AUDITORIA_CADEIA SET ULTIMA_SEQ = ?, ULTIMO_HASH = ?, ATUALIZADA_EM = SYSTIMESTAMP"
                + " WHERE ID = 1", ultimaSeq, ultimoHash);
    }

    /**
     * Percorre os registros selados com {@code SEQ_CADEIA <= ateSeq} em ordem de sequência, sem carregar tudo na
     * memória; para quando o consumidor devolver {@code false}.
     */
    public void percorrerSelados(long ateSeq, Predicate<Selado> consumidor) {
        jdbc.query(con -> {
            var ps = con.prepareStatement("SELECT " + COLUNAS + ", SEQ_CADEIA, HASH_ANTERIOR, HASH_REGISTRO"
                    + " FROM AUDITORIA WHERE SEQ_CADEIA IS NOT NULL AND SEQ_CADEIA <= ? ORDER BY SEQ_CADEIA");
            ps.setLong(1, ateSeq);
            ps.setFetchSize(500);
            return ps;
        }, (ResultSet rs) -> {
            while (rs.next()) {
                Selado s = new Selado(registro(rs), rs.getLong("SEQ_CADEIA"), rs.getString("HASH_ANTERIOR"),
                        rs.getString("HASH_REGISTRO"));
                if (!consumidor.test(s)) {
                    return null;
                }
            }
            return null;
        });
    }

    public long contarLegados(long inicioId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM AUDITORIA WHERE ID <= ?", Long.class, inicioId);
        return n == null ? 0 : n;
    }

    public long contarPendentes(long inicioId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM AUDITORIA WHERE (CASE WHEN HASH_REGISTRO IS NULL THEN ID END) > ?",
                Long.class, inicioId);
        return n == null ? 0 : n;
    }

    private static Registro registro(ResultSet rs) throws SQLException {
        long usuario = rs.getLong("USUARIO_ID");
        Long usuarioId = rs.wasNull() ? null : usuario;
        return new Registro(rs.getLong("ID"), rs.getObject("DATA", LocalDateTime.class), usuarioId,
                rs.getString("ACAO"), rs.getString("ENTIDADE"), rs.getString("ENTIDADE_ID"), rs.getString("DETALHE"),
                rs.getString("VALOR_ANTERIOR"), rs.getString("VALOR_NOVO"));
    }
}
