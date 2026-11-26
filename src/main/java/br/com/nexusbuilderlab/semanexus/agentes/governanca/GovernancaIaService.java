package br.com.nexusbuilderlab.semanexus.agentes.governanca;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Governança de IA (Issue #131): registra a última sugestão de cada agente por demanda, compara com a decisão humana
 * e guarda o feedback do DocIA na tabela {@code AVALIACAO_AGENTE}.
 * <p>
 * O registro é telemetria, nunca dado operacional: {@link #registrarSugestao} executa o par {@code DELETE + INSERT} em
 * transação própria ({@code REQUIRES_NEW}), garantindo atomicidade quando chamado fora de transação pelos agentes e
 * rollback conjunto se o {@code INSERT} falhar; os demais métodos de registro participam da transação do chamador
 * quando houver. Todos <b>engolem</b> qualquer falha com um aviso no log — a triagem, a distribuição e as respostas
 * dos agentes nunca falham por causa da governança. No Oracle, um comando com erro não invalida a transação do
 * chamador.
 */
@Service
public class GovernancaIaService {

    public static final String AGENTE_TRIAGEM = "triagem";
    public static final String AGENTE_DISTRIBUICAO = "distribuicao";
    public static final String AGENTE_DOCIA = "docia";
    /** Sugestão mais antiga que isto não é comparada com a decisão humana. */
    public static final int JANELA_SUGESTAO_DIAS = 7;
    public static final int PERIODO_PADRAO_DIAS = 30;
    static final String SEM_CATEGORIA = "SEM_CATEGORIA";

    private static final Logger LOG = LoggerFactory.getLogger(GovernancaIaService.class);
    private static final Pattern UUID_VALIDO =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final JdbcTemplate jdbc;
    private final AuditService auditoria;
    private final Clock clock;
    private final TransactionTemplate novaTransacao;

    public GovernancaIaService(JdbcTemplate jdbc, AuditService auditoria, Clock clock) {
        this(jdbc, auditoria, clock, (PlatformTransactionManager) null);
    }

    GovernancaIaService(JdbcTemplate jdbc, AuditService auditoria, Clock clock, PlatformTransactionManager transacoes) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.clock = clock;
        if (transacoes == null) {
            this.novaTransacao = null;
        } else {
            this.novaTransacao = new TransactionTemplate(transacoes);
            this.novaTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
    }

    @Autowired
    public GovernancaIaService(JdbcTemplate jdbc, AuditService auditoria, Clock clock,
                               ObjectProvider<PlatformTransactionManager> transacoes) {
        this(jdbc, auditoria, clock, transacoes.getIfAvailable());
    }

    private Timestamp agora() {
        return Timestamp.valueOf(LocalDateTime.now(clock));
    }

    /**
     * Guarda a sugestão como PENDENTE em transação própria ({@code REQUIRES_NEW}), substituindo a pendente anterior do
     * mesmo agente na mesma demanda (fica só a última). {@code campos}: o que a pessoa poderá aplicar (ex.: decisao,
     * categoria, prioridade).
     */
    public void registrarSugestao(String agente, Long demandaId, Long usuarioId, String modo,
                                  Map<String, String> campos) {
        if (demandaId == null || usuarioId == null) {
            return;
        }
        try {
            if (novaTransacao == null) {
                gravarSugestao(agente, demandaId, usuarioId, modo, campos);
            } else {
                novaTransacao.executeWithoutResult(st -> gravarSugestao(agente, demandaId, usuarioId, modo, campos));
            }
        } catch (RuntimeException e) {
            LOG.warn("Governança de IA: sugestão do agente {} na demanda {} não registrada ({})", agente, demandaId,
                    e.getClass().getSimpleName());
        }
    }

    private void gravarSugestao(String agente, Long demandaId, Long usuarioId, String modo,
                                Map<String, String> campos) {
        jdbc.update("DELETE FROM AVALIACAO_AGENTE WHERE AGENTE = ? AND DEMANDA_ID = ? AND RESULTADO = 'PENDENTE'",
                agente, demandaId);
        jdbc.update("INSERT INTO AVALIACAO_AGENTE (AGENTE, DEMANDA_ID, USUARIO_ID, MODO, SUGERIDO, RESULTADO, "
                        + "CRIADO_EM) VALUES (?, ?, ?, ?, ?, 'PENDENTE', ?)",
                agente, demandaId, usuarioId, cortar(modo, 20), cortar(ComparadorDecisao.serializar(campos), 1000),
                agora());
    }

    /**
     * Compara a decisão humana com a última sugestão PENDENTE recente (até {@value #JANELA_SUGESTAO_DIAS} dias) do
     * agente na demanda. Sem sugestão recente, não registra nada. Retorna a comparação feita (ou nula).
     */
    public ComparadorDecisao.Comparacao registrarDecisao(String agente, Long demandaId, Long avaliadorId,
                                                         String categoria, Map<String, String> aplicado,
                                                         String campoDecisivo) {
        if (demandaId == null) {
            return null;
        }
        try {
            Timestamp limite = Timestamp.valueOf(LocalDateTime.now(clock).minusDays(JANELA_SUGESTAO_DIAS));
            List<Map<String, Object>> linhas = jdbc.queryForList(
                    "SELECT ID, SUGERIDO FROM AVALIACAO_AGENTE WHERE AGENTE = ? AND DEMANDA_ID = ? "
                            + "AND RESULTADO = 'PENDENTE' AND CRIADO_EM >= ? ORDER BY CRIADO_EM DESC, ID DESC "
                            + "FETCH FIRST 1 ROWS ONLY", agente, demandaId, limite);
            if (linhas.isEmpty()) {
                return null;
            }
            Map<String, Object> linha = linhas.get(0);
            Map<String, String> sugerido = ComparadorDecisao.desserializar((String) linha.get("SUGERIDO"));
            ComparadorDecisao.Comparacao c = ComparadorDecisao.comparar(sugerido, aplicado, campoDecisivo);
            jdbc.update("UPDATE AVALIACAO_AGENTE SET RESULTADO = ?, APLICADO = ?, CAMPOS_DIVERGENTES = ?, "
                            + "CATEGORIA = ?, AVALIADOR_ID = ?, AVALIADO_EM = ? WHERE ID = ? AND RESULTADO = 'PENDENTE'",
                    c.resultado().name(), cortar(ComparadorDecisao.serializar(aplicado), 1000),
                    cortar(String.join(",", c.camposDivergentes()), 200), cortar(categoria, 40), avaliadorId, agora(),
                    ((Number) linha.get("ID")).longValue());
            return c;
        } catch (RuntimeException e) {
            LOG.warn("Governança de IA: decisão do agente {} na demanda {} não registrada ({})", agente, demandaId,
                    e.getClass().getSimpleName());
            return null;
        }
    }

    /** Registra uma resposta do DocIA e devolve o identificador da execução (nulo se não foi possível registrar). */
    public String registrarResposta(Long usuarioId, String modo) {
        if (usuarioId == null) {
            return null;
        }
        String execucaoId = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO AVALIACAO_AGENTE (AGENTE, EXECUCAO_ID, USUARIO_ID, MODO, RESULTADO, CRIADO_EM) "
                    + "VALUES ('docia', ?, ?, ?, 'PENDENTE', ?)", execucaoId, usuarioId, cortar(modo, 20), agora());
            return execucaoId;
        } catch (RuntimeException e) {
            LOG.warn("Governança de IA: resposta do DocIA não registrada ({})", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Feedback UTIL/IMPRECISA do autor da pergunta. 404 se a execução não existe ou é de outro usuário (não revela
     * respostas alheias). Pode ser trocado depois. Auditado.
     */
    @Transactional
    public void avaliarResposta(UsuarioAutenticado ator, String execucaoId,
                                AvaliacaoRespostaRequest.Avaliacao avaliacao) {
        String id = execucaoId == null ? "" : execucaoId.strip().toLowerCase(Locale.ROOT);
        int n = !UUID_VALIDO.matcher(id).matches() ? 0 : jdbc.update(
                "UPDATE AVALIACAO_AGENTE SET RESULTADO = ?, AVALIADOR_ID = ?, AVALIADO_EM = ? "
                        + "WHERE EXECUCAO_ID = ? AND AGENTE = 'docia' AND USUARIO_ID = ?",
                avaliacao.name(), ator.id(), agora(), id, ator.id());
        if (n == 0) {
            throw new RecursoNaoEncontradoException("Resposta do assistente não encontrada.");
        }
        auditoria.registrar("DOCIA_AVALIAR_RESPOSTA", "AVALIACAO_AGENTE", id,
                "Resposta do Assistente DocIA avaliada como " + avaliacao.name() + ".");
    }

    /** Estatísticas dos últimos {@code dias} dias (pela data da decisão/feedback). */
    @Transactional(readOnly = true)
    public GovernancaIa estatisticas(int dias) {
        int periodo = Math.max(1, Math.min(dias, 365));
        Timestamp desde = Timestamp.valueOf(LocalDateTime.now(clock).minusDays(periodo));
        List<Map<String, Object>> linhas = jdbc.queryForList(
                "SELECT AGENTE, CATEGORIA, RESULTADO, COUNT(*) AS TOTAL FROM AVALIACAO_AGENTE "
                        + "WHERE RESULTADO <> 'PENDENTE' AND AVALIADO_EM >= ? GROUP BY AGENTE, CATEGORIA, RESULTADO",
                desde);
        return agregar(periodo, linhas);
    }

    static GovernancaIa agregar(int periodo, List<Map<String, Object>> linhas) {
        Map<String, long[]> porAgente = new TreeMap<>();
        Map<String, long[]> porCategoria = new TreeMap<>();
        long util = 0;
        long imprecisa = 0;
        for (Map<String, Object> l : linhas) {
            String agente = (String) l.get("AGENTE");
            String resultado = String.valueOf(l.get("RESULTADO"));
            long total = ((Number) l.get("TOTAL")).longValue();
            if (AGENTE_DOCIA.equals(agente)) {
                if (ResultadoAvaliacao.UTIL.name().equals(resultado)) {
                    util += total;
                } else if (ResultadoAvaliacao.IMPRECISA.name().equals(resultado)) {
                    imprecisa += total;
                }
                continue;
            }
            int i = switch (resultado) {
                case "ACEITA_INTEGRAL" -> 0;
                case "AJUSTADA_PARCIAL" -> 1;
                case "DESCARTADA" -> 2;
                default -> -1;
            };
            if (i < 0) {
                continue;
            }
            String categoria = l.get("CATEGORIA") == null ? SEM_CATEGORIA : (String) l.get("CATEGORIA");
            porAgente.computeIfAbsent(agente, k -> new long[3])[i] += total;
            porCategoria.computeIfAbsent(agente + "|" + categoria, k -> new long[3])[i] += total;
        }
        List<GovernancaIa.Concordancia> agentes = new ArrayList<>();
        porAgente.forEach((a, c) -> agentes.add(concordancia(a, null, c)));
        List<GovernancaIa.Concordancia> categorias = new ArrayList<>();
        porCategoria.forEach((k, c) -> {
            String[] p = k.split("\\|", 2);
            categorias.add(concordancia(p[0], p[1], c));
        });
        return new GovernancaIa(periodo, agentes, categorias,
                new GovernancaIa.FeedbackDocia(util, imprecisa, pct(util, util + imprecisa)));
    }

    private static GovernancaIa.Concordancia concordancia(String agente, String categoria, long[] c) {
        long total = c[0] + c[1] + c[2];
        return new GovernancaIa.Concordancia(agente, categoria, total, c[0], c[1], c[2], pct(c[0], total),
                pct(c[0] + c[1], total));
    }

    static Double pct(long parte, long total) {
        return total == 0 ? null : Math.round(parte * 1000.0 / total) / 10.0;
    }

    private static String cortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** Campos em ordem estável a partir de pares chave/valor (valores podem ser nulos). */
    public static Map<String, String> campos(String... chaveValor) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < chaveValor.length; i += 2) {
            m.put(chaveValor[i], chaveValor[i + 1]);
        }
        return m;
    }
}
