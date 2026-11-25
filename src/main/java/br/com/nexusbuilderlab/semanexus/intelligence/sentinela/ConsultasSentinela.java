package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.TipoReferencia;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.RegrasSentinela.ContagemInpe;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.RegrasSentinela.ItemFila;

import jakarta.persistence.EntityManager;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas somente leitura da Sentinela Territorial (Issue #138): agregações no banco (SQL/JPQL parametrizados, sem
 * N+1) sobre a camada do INPE e as demandas. Nada é gravado. ADMIN e GESTOR (únicos perfis do briefing) enxergam todas
 * as demandas, então as consultas não aplicam escopo por usuário.
 */
@Service
@Transactional(readOnly = true)
public class ConsultasSentinela {

    /** Limite de pontos lidos por município para achar o agrupamento mais denso. */
    static final int MAX_PONTOS_AGRUPAMENTO = 5000;

    /** Janela recente de um tipo do INPE: (de, ate], com {@code ate} = data/hora mais recente do pacote. */
    public record Janela(TipoReferencia tipo, LocalDateTime de, LocalDateTime ate) {
    }

    private final JdbcTemplate jdbc;
    private final EntityManager em;

    public ConsultasSentinela(JdbcTemplate jdbc, EntityManager em) {
        this.jdbc = jdbc;
        this.em = em;
    }

    /** Data/hora mais recente do tipo no pacote do INPE (o "agora" das janelas, já que o pacote é estático). */
    public Optional<LocalDateTime> ultimaDataInpe(TipoReferencia tipo) {
        Timestamp t = jdbc.queryForObject("SELECT MAX(DATA_HORA) FROM REFERENCIA_SATELITE WHERE TIPO = ?",
                Timestamp.class, tipo.name());
        return Optional.ofNullable(t).map(Timestamp::toLocalDateTime);
    }

    /** Focos e DETER por município dentro das janelas informadas (uma agregação; municípios sem registro ficam fora). */
    public List<ContagemInpe> inpePorMunicipio(List<Janela> janelas) {
        if (janelas.isEmpty()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        String where = filtroJanelas(janelas, args);
        return jdbc.query("SELECT r.MUNICIPIO_ID, m.NOME, "
                        + "SUM(CASE WHEN r.TIPO = 'FOCO_CALOR' THEN 1 ELSE 0 END) AS FOCOS, "
                        + "SUM(CASE WHEN r.TIPO = 'DESMATAMENTO' THEN 1 ELSE 0 END) AS DETER "
                        + "FROM REFERENCIA_SATELITE r JOIN MUNICIPIO m ON m.ID = r.MUNICIPIO_ID WHERE " + where
                        + " GROUP BY r.MUNICIPIO_ID, m.NOME",
                (rs, i) -> new ContagemInpe(rs.getInt("MUNICIPIO_ID"), rs.getString("NOME"), rs.getLong("FOCOS"),
                        rs.getLong("DETER")), args.toArray());
    }

    /** Coordenadas (lat, lon) dos registros do município nas janelas, para o agrupamento mais denso. */
    public List<double[]> pontosInpe(int municipioId, List<Janela> janelas) {
        if (janelas.isEmpty()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        args.add(municipioId);
        String where = filtroJanelas(janelas, args);
        args.add(MAX_PONTOS_AGRUPAMENTO);
        return jdbc.query("SELECT r.LATITUDE, r.LONGITUDE FROM REFERENCIA_SATELITE r WHERE r.MUNICIPIO_ID = ? AND "
                        + where + " ORDER BY r.DATA_HORA DESC, r.ID FETCH FIRST ? ROWS ONLY",
                (rs, i) -> new double[] {rs.getDouble(1), rs.getDouble(2)}, args.toArray());
    }

    private static String filtroJanelas(List<Janela> janelas, List<Object> args) {
        List<String> partes = new ArrayList<>();
        for (Janela j : janelas) {
            partes.add("(r.TIPO = ? AND r.DATA_HORA > ? AND r.DATA_HORA <= ?)");
            args.add(j.tipo().name());
            args.add(Timestamp.valueOf(j.de()));
            args.add(Timestamp.valueOf(j.ate()));
        }
        return "(" + String.join(" OR ", partes) + ")";
    }

    /** Demandas não finalizadas por município (código IBGE → quantidade). */
    public Map<Integer, Long> abertasPorMunicipio() {
        Map<Integer, Long> m = new LinkedHashMap<>();
        em.createQuery("""
                select d.municipio.id, count(d) from Demanda d
                where d.status <> :finalizada group by d.municipio.id""", Object[].class)
                .setParameter("finalizada", StatusDemanda.FINALIZADA)
                .getResultList()
                .forEach(l -> m.put(((Number) l[0]).intValue(), ((Number) l[1]).longValue()));
        return m;
    }

    /** Demandas não finalizadas por status, na ordem do fluxo (status sem demanda aparecem com zero). */
    public Map<String, Long> abertasPorStatus() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (StatusDemanda s : StatusDemanda.values()) {
            if (s != StatusDemanda.FINALIZADA) {
                m.put(s.name(), 0L);
            }
        }
        em.createQuery("""
                select d.status, count(d) from Demanda d
                where d.status <> :finalizada group by d.status""", Object[].class)
                .setParameter("finalizada", StatusDemanda.FINALIZADA)
                .getResultList()
                .forEach(l -> m.put(((StatusDemanda) l[0]).name(), ((Number) l[1]).longValue()));
        return m;
    }

    /**
     * Demandas nos status de fila e desde quando estão no status atual: o último evento do histórico que levou a esse
     * status ou, sem ele, a criação da demanda. Uma agregação (left join + max), sem N+1.
     */
    public List<ItemFila> itensFila() {
        return em.createQuery("""
                select d.id, d.numero, d.status, d.criadoEm, max(h.data) from Demanda d
                left join DemandaHistorico h on h.demanda = d and h.statusNovo = d.status
                where d.status in :status
                group by d.id, d.numero, d.status, d.criadoEm""", Object[].class)
                .setParameter("status", RegrasSentinela.STATUS_FILA)
                .getResultList().stream()
                .map(l -> new ItemFila((Long) l[0], (String) l[1], (StatusDemanda) l[2],
                        l[4] != null ? (LocalDateTime) l[4] : (LocalDateTime) l[3]))
                .toList();
    }
}
