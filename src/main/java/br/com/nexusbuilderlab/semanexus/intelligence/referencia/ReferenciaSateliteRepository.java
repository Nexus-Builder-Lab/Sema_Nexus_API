package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.ContagemMunicipio;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.Importacao;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.Ponto;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Acesso JDBC (SQL parametrizado) às tabelas {@code IMPORTACAO_REFERENCIA} e {@code REFERENCIA_SATELITE}. */
@Repository
public class ReferenciaSateliteRepository {

    /** Linha pronta para inserção. */
    public record Linha(TipoReferencia tipo, LocalDateTime dataHora, BigDecimal latitude, BigDecimal longitude,
                        int municipioId, String bioma, String satelite, String classe, BigDecimal areaKm2,
                        String atributos) {
    }

    private final JdbcTemplate jdbc;

    public ReferenciaSateliteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existeImportacao(String sha256) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM IMPORTACAO_REFERENCIA WHERE SHA256 = ?",
                Integer.class, sha256);
        return n != null && n > 0;
    }

    /** Grava a importação e seus pontos; devolve o ID da importação. */
    public long gravar(String fonte, TipoReferencia tipo, LocalDate de, LocalDate ate, String url, String licenca,
                       String arquivo, String sha256, List<Linha> linhas) {
        jdbc.update("INSERT INTO IMPORTACAO_REFERENCIA (FONTE, TIPO, PERIODO_DE, PERIODO_ATE, REGISTROS, URL_ORIGEM, "
                        + "LICENCA, ARQUIVO, SHA256) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                fonte, tipo.name(), java.sql.Date.valueOf(de), java.sql.Date.valueOf(ate), linhas.size(), url,
                licenca, arquivo, sha256);
        Long id = jdbc.queryForObject("SELECT ID FROM IMPORTACAO_REFERENCIA WHERE SHA256 = ?", Long.class, sha256);
        jdbc.batchUpdate("INSERT INTO REFERENCIA_SATELITE (IMPORTACAO_ID, TIPO, DATA_HORA, LATITUDE, LONGITUDE, "
                        + "MUNICIPIO_ID, BIOMA, SATELITE, CLASSE, AREA_KM2, ATRIBUTOS) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                linhas, 1000, (ps, l) -> {
                    ps.setLong(1, id);
                    ps.setString(2, l.tipo().name());
                    ps.setTimestamp(3, Timestamp.valueOf(l.dataHora()));
                    ps.setBigDecimal(4, l.latitude());
                    ps.setBigDecimal(5, l.longitude());
                    ps.setInt(6, l.municipioId());
                    ps.setString(7, l.bioma());
                    ps.setString(8, l.satelite());
                    ps.setString(9, l.classe());
                    ps.setBigDecimal(10, l.areaKm2());
                    ps.setString(11, l.atributos());
                });
        return id;
    }

    public List<Importacao> importacoes() {
        return jdbc.query("SELECT ID, FONTE, TIPO, PERIODO_DE, PERIODO_ATE, IMPORTADO_EM, REGISTROS, URL_ORIGEM, "
                        + "LICENCA, ARQUIVO FROM IMPORTACAO_REFERENCIA ORDER BY TIPO, ID",
                (rs, i) -> new Importacao(rs.getLong("ID"), rs.getString("FONTE"), rs.getString("TIPO"),
                        rs.getDate("PERIODO_DE").toLocalDate(), rs.getDate("PERIODO_ATE").toLocalDate(),
                        rs.getTimestamp("IMPORTADO_EM").toLocalDateTime(), rs.getLong("REGISTROS"),
                        rs.getString("URL_ORIGEM"), rs.getString("LICENCA"), rs.getString("ARQUIVO")));
    }

    /** Até {@code limite} pontos mais recentes que atendem ao filtro. */
    public List<Ponto> pontos(ReferenciaFiltro f, int limite) {
        List<Object> args = new ArrayList<>();
        String where = where(f, args);
        args.add(limite);
        return jdbc.query("SELECT r.ID, r.TIPO, r.DATA_HORA, r.LATITUDE, r.LONGITUDE, r.MUNICIPIO_ID, m.NOME, "
                        + "r.BIOMA, r.SATELITE, r.CLASSE, r.AREA_KM2 FROM REFERENCIA_SATELITE r "
                        + "JOIN MUNICIPIO m ON m.ID = r.MUNICIPIO_ID" + where
                        + " ORDER BY r.DATA_HORA DESC, r.ID FETCH FIRST ? ROWS ONLY",
                (rs, i) -> new Ponto(rs.getLong("ID"), rs.getString("TIPO"),
                        rs.getTimestamp("DATA_HORA").toLocalDateTime(), rs.getBigDecimal("LATITUDE"),
                        rs.getBigDecimal("LONGITUDE"), rs.getInt("MUNICIPIO_ID"), rs.getString("NOME"),
                        rs.getString("BIOMA"), rs.getString("SATELITE"), rs.getString("CLASSE"),
                        rs.getBigDecimal("AREA_KM2")), args.toArray());
    }

    public List<ContagemMunicipio> porMunicipio(ReferenciaFiltro f) {
        List<Object> args = new ArrayList<>();
        String where = where(f, args);
        return jdbc.query("SELECT r.MUNICIPIO_ID, m.NOME, m.BIOMA, COUNT(*) AS TOTAL FROM REFERENCIA_SATELITE r "
                        + "JOIN MUNICIPIO m ON m.ID = r.MUNICIPIO_ID" + where
                        + " GROUP BY r.MUNICIPIO_ID, m.NOME, m.BIOMA ORDER BY TOTAL DESC, m.NOME",
                (rs, i) -> new ContagemMunicipio(rs.getInt("MUNICIPIO_ID"), rs.getString("NOME"),
                        rs.getString("BIOMA"), rs.getLong("TOTAL")), args.toArray());
    }

    private static String where(ReferenciaFiltro f, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE 1 = 1");
        if (f.tipo() != null) {
            sb.append(" AND r.TIPO = ?");
            args.add(f.tipo().name());
        }
        if (f.de() != null) {
            sb.append(" AND r.DATA_HORA >= ?");
            args.add(Timestamp.valueOf(f.de().atStartOfDay()));
        }
        if (f.ate() != null) {
            sb.append(" AND r.DATA_HORA < ?");
            args.add(Timestamp.valueOf(f.ate().plusDays(1).atStartOfDay()));
        }
        if (f.municipioId() != null) {
            sb.append(" AND r.MUNICIPIO_ID = ?");
            args.add(f.municipioId());
        }
        if (f.bioma() != null) {
            sb.append(" AND r.BIOMA = ?");
            args.add(f.bioma().name());
        }
        return sb.toString();
    }
}
