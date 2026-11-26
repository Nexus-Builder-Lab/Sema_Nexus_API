package br.com.nexusbuilderlab.semanexus.intelligence.camada;

import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.Importacao;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

/** Acesso JDBC (SQL parametrizado) às tabelas {@code IMPORTACAO_CAMADA_PUBLICA} e {@code CAMADA_PUBLICA_FEICAO}. */
@Repository
public class CamadaPublicaRepository {

    public record Linha(
            TipoCamadaPublica camada,
            String codigoOrigem,
            String nome,
            String categoria,
            String esfera,
            BigDecimal areaHa,
            BigDecimal centroidLat,
            BigDecimal centroidLon,
            BigDecimal latMin,
            BigDecimal latMax,
            BigDecimal lonMin,
            BigDecimal lonMax,
            String tipoGeometria,
            String atributos,
            String geometria) {
    }

    public record Feicao(
            Long id,
            Long importacaoId,
            TipoCamadaPublica camada,
            String codigoOrigem,
            String nome,
            String categoria,
            String esfera,
            BigDecimal areaHa,
            String atributos,
            String geometria) {
    }

    private final JdbcTemplate jdbc;

    public CamadaPublicaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existeImportacao(String sha256) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM IMPORTACAO_CAMADA_PUBLICA WHERE SHA256 = ?",
                Integer.class, sha256);
        return n != null && n > 0;
    }

    /** Remove importações (e feições) anteriores da camada; devolve quantas importações foram removidas. */
    public int removerImportacoes(TipoCamadaPublica camada) {
        jdbc.update("DELETE FROM CAMADA_PUBLICA_FEICAO WHERE IMPORTACAO_ID IN "
                + "(SELECT ID FROM IMPORTACAO_CAMADA_PUBLICA WHERE CAMADA = ?)", camada.name());
        return jdbc.update("DELETE FROM IMPORTACAO_CAMADA_PUBLICA WHERE CAMADA = ?", camada.name());
    }

    public long gravar(TipoCamadaPublica camada, String fonte, LocalDate dataReferencia, String urlOrigem,
                       String licenca, String arquivo, String sha256, List<Linha> linhas) {
        jdbc.update("INSERT INTO IMPORTACAO_CAMADA_PUBLICA (CAMADA, FONTE, DATA_REFERENCIA, REGISTROS, URL_ORIGEM, "
                        + "LICENCA, ARQUIVO, SHA256) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                camada.name(), fonte, Date.valueOf(dataReferencia), linhas.size(), urlOrigem, licenca, arquivo, sha256);
        Long id = jdbc.queryForObject("SELECT ID FROM IMPORTACAO_CAMADA_PUBLICA WHERE SHA256 = ?", Long.class, sha256);
        List<Object[]> lote = new ArrayList<>(linhas.size());
        for (Linha l : linhas) {
            lote.add(new Object[] {
                    id,
                    l.camada().name(),
                    l.codigoOrigem(),
                    l.nome(),
                    l.categoria(),
                    l.esfera(),
                    l.areaHa(),
                    l.centroidLat(),
                    l.centroidLon(),
                    l.latMin(),
                    l.latMax(),
                    l.lonMin(),
                    l.lonMax(),
                    l.tipoGeometria(),
                    new SqlParameterValue(Types.CLOB, l.atributos()),
                    new SqlParameterValue(Types.CLOB, l.geometria())
            });
        }
        String sql = "INSERT INTO CAMADA_PUBLICA_FEICAO (IMPORTACAO_ID, CAMADA, CODIGO_ORIGEM, NOME, CATEGORIA, "
                + "ESFERA, AREA_HA, CENTROID_LAT, CENTROID_LON, LAT_MIN, LAT_MAX, LON_MIN, LON_MAX, "
                + "TIPO_GEOMETRIA, ATRIBUTOS, GEOMETRIA) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        for (int i = 0; i < lote.size(); i += 500) {
            jdbc.batchUpdate(sql, lote.subList(i, Math.min(i + 500, lote.size())));
        }
        return id;
    }

    public List<Importacao> importacoes() {
        return jdbc.query("SELECT ID, CAMADA, FONTE, DATA_REFERENCIA, IMPORTADO_EM, REGISTROS, URL_ORIGEM, "
                        + "LICENCA, ARQUIVO FROM IMPORTACAO_CAMADA_PUBLICA ORDER BY CAMADA, ID",
                (rs, i) -> new Importacao(
                        rs.getLong("ID"),
                        TipoCamadaPublica.valueOf(rs.getString("CAMADA")),
                        rs.getString("FONTE"),
                        rs.getDate("DATA_REFERENCIA").toLocalDate(),
                        rs.getTimestamp("IMPORTADO_EM").toLocalDateTime(),
                        rs.getLong("REGISTROS"),
                        rs.getString("URL_ORIGEM"),
                        rs.getString("LICENCA"),
                        rs.getString("ARQUIVO")));
    }

    public List<Feicao> feicoes(TipoCamadaPublica camada, String esfera, Integer nivel, String termo, int limite) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT ID, IMPORTACAO_ID, CAMADA, CODIGO_ORIGEM, NOME, CATEGORIA, ESFERA, AREA_HA, ATRIBUTOS, GEOMETRIA "
                        + "FROM CAMADA_PUBLICA_FEICAO WHERE CAMADA = ?");
        args.add(camada.name());
        if (esfera != null && !esfera.isBlank()) {
            sql.append(" AND UPPER(ESFERA) = ?");
            args.add(esfera.strip().toUpperCase());
        }
        if (nivel != null) {
            sql.append(" AND UPPER(ESFERA) = ?");
            args.add("NÍVEL " + nivel);
        }
        if (termo != null && !termo.isBlank()) {
            sql.append(" AND LOWER(NOME) LIKE ? ESCAPE '\\'");
            args.add("%" + escaparLike(termo.strip().toLowerCase()) + "%");
        }
        sql.append(" ORDER BY NOME, ID FETCH FIRST ? ROWS ONLY");
        args.add(limite);
        return jdbc.query(sql.toString(), (rs, i) -> mapFeicao(rs, true), args.toArray());
    }

    /** Pré-filtro espacial por camada e interseção de caixa envolvente usando {@code IX_CAMPUB_BBOX}. */
    public List<Feicao> naCaixa(TipoCamadaPublica camada, double latMin, double latMax, double lonMin, double lonMax,
                                int limite) {
        return jdbc.query(
                "SELECT ID, IMPORTACAO_ID, CAMADA, CODIGO_ORIGEM, NOME, CATEGORIA, ESFERA, AREA_HA, GEOMETRIA "
                        + "FROM CAMADA_PUBLICA_FEICAO WHERE CAMADA = ? AND LAT_MAX >= ? AND LAT_MIN <= ? "
                        + "AND LON_MAX >= ? AND LON_MIN <= ? ORDER BY ID FETCH FIRST ? ROWS ONLY",
                (rs, i) -> mapFeicao(rs, false),
                camada.name(), latMin, latMax, lonMin, lonMax, limite);
    }

    private static Feicao mapFeicao(java.sql.ResultSet rs, boolean comAtributos) throws java.sql.SQLException {
        return new Feicao(
                rs.getLong("ID"),
                rs.getLong("IMPORTACAO_ID"),
                TipoCamadaPublica.valueOf(rs.getString("CAMADA")),
                rs.getString("CODIGO_ORIGEM"),
                rs.getString("NOME"),
                rs.getString("CATEGORIA"),
                rs.getString("ESFERA"),
                rs.getBigDecimal("AREA_HA"),
                comAtributos ? rs.getString("ATRIBUTOS") : null,
                rs.getString("GEOMETRIA"));
    }

    private static String escaparLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
