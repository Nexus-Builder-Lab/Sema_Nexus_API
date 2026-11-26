package br.com.nexusbuilderlab.semanexus.core.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.IntegridadeAuditoria;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Cadeia SHA-256 da auditoria no Oracle real (Issue #148): V14, inserções e selagens concorrentes sem bifurcar a
 * cadeia, rollback sem registro, antes/depois persistido, registro legado fora da cadeia e adulteração detectada.
 * Fora do build padrão: {@code mvn -B -ntp verify -Poracle}.
 */
@Tag("oracle")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditoriaCadeiaOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    @Autowired
    AuditService auditoria;
    @Autowired
    SeladorAuditoria selador;
    @Autowired
    IntegridadeAuditoriaService integridade;
    @Autowired
    AuditoriaRepository repositorio;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate tx;

    private long pendentes() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM AUDITORIA a, AUDITORIA_CADEIA c"
                + " WHERE c.ID = 1 AND a.ID > c.INICIO_ID AND a.HASH_REGISTRO IS NULL", Long.class);
        return n == null ? 0 : n;
    }

    @Test
    @Order(1)
    void insercoesESelagensConcorrentesMantemACadeiaIntegra() throws Exception {
        int threads = 8;
        int porThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads + 2);
        try {
            List<Callable<Void>> tarefas = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int n = t;
                tarefas.add(() -> {
                    for (int i = 0; i < porThread; i++) {
                        int k = i;
                        tx.executeWithoutResult(s -> auditoria.registrarComo(null, "TESTE_CONCORRENCIA", "TESTE",
                                n + "-" + k, "Inserção concorrente " + n + "/" + k));
                    }
                    return null;
                });
            }
            // Selagens síncronas disputando a trava ao mesmo tempo que as selagens assíncronas pós-commit
            // (simula várias instâncias da API).
            for (int s = 0; s < 2; s++) {
                tarefas.add(() -> {
                    for (int i = 0; i < 20; i++) {
                        selador.selarPendentes();
                    }
                    return null;
                });
            }
            for (Future<Void> f : pool.invokeAll(tarefas)) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        selador.selarPendentes();
        assertThat(pendentes()).isZero();

        Long distintas = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT SEQ_CADEIA) FROM AUDITORIA WHERE ACAO = 'TESTE_CONCORRENCIA'", Long.class);
        assertThat(distintas).isEqualTo((long) threads * porThread);

        IntegridadeAuditoria r = integridade.verificar();
        assertThat(r.integra()).as("primeira falha: %s", r.primeiraFalha()).isTrue();
        assertThat(r.registrosVerificados()).isGreaterThanOrEqualTo((long) threads * porThread);
        assertThat(r.registrosVerificados()).isEqualTo(r.ultimaSequencia());
    }

    @Test
    @Order(2)
    void rollbackNaoDeixaRegistroEAntesDepoisEhPersistido() {
        try {
            tx.executeWithoutResult(s -> {
                auditoria.registrarComo(null, "TESTE_ROLLBACK", "TESTE", "1", "não deve ficar");
                s.setRollbackOnly();
            });
        } finally {
            selador.selarPendentes();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AUDITORIA WHERE ACAO = 'TESTE_ROLLBACK'", Long.class))
                .isZero();

        tx.executeWithoutResult(s -> auditoria.registrarAlteracao("ATUALIZAR", "TESTE", "77", "Edição de teste.",
                Alteracoes.nova().campo("nome", "Antigo", "Novo").campo("ativo", true, true)));
        selador.selarPendentes();

        var dto = auditoria.historicoDaEntidade("TESTE", "77").get(0);
        assertThat(dto.valorAnterior()).containsExactlyEntriesOf(java.util.Map.of("nome", "Antigo"));
        assertThat(dto.valorNovo()).containsExactlyEntriesOf(java.util.Map.of("nome", "Novo"));
        assertThat(dto.seqCadeia()).isNotNull();
        assertThat(dto.hashRegistro()).hasSize(64);
        assertThat(integridade.verificar().integra()).isTrue();
    }

    @Test
    @Order(3)
    void registroLegadoFicaForaDaCadeia() {
        // ID explícito abaixo do início da cadeia = registro anterior à V14.
        jdbc.update("INSERT INTO AUDITORIA (ID, ACAO, ENTIDADE, DETALHE) VALUES (0, 'LEGADO', 'TESTE', 'antigo')");
        selador.selarPendentes();

        assertThat(jdbc.queryForObject("SELECT HASH_REGISTRO FROM AUDITORIA WHERE ID = 0", String.class)).isNull();
        IntegridadeAuditoria r = integridade.verificar();
        assertThat(r.integra()).isTrue();
        assertThat(r.registrosLegados()).isGreaterThanOrEqualTo(1);
        assertThat(r.registrosPendentes()).isZero();
    }

    @Test
    @Order(4)
    void adulteracaoEhDetectada() {
        long alvo = 5;
        String original = jdbc.queryForObject("SELECT DETALHE FROM AUDITORIA WHERE SEQ_CADEIA = ?", String.class,
                alvo);
        jdbc.update("UPDATE AUDITORIA SET DETALHE = 'adulterado' WHERE SEQ_CADEIA = ?", alvo);

        IntegridadeAuditoria r = integridade.verificar();
        assertThat(r.integra()).isFalse();
        assertThat(r.situacao()).isEqualTo("VIOLADA");
        assertThat(r.primeiraFalha().motivo()).isEqualTo("HASH_DIVERGENTE");
        assertThat(r.primeiraFalha().sequencia()).isEqualTo(alvo);

        jdbc.update("UPDATE AUDITORIA SET DETALHE = ? WHERE SEQ_CADEIA = ?", original, alvo);
        assertThat(integridade.verificar().integra()).isTrue();

        // Remoção do último registro selado: o fim não confere com o estado da cadeia.
        jdbc.update("DELETE FROM AUDITORIA WHERE SEQ_CADEIA = (SELECT ULTIMA_SEQ FROM AUDITORIA_CADEIA WHERE ID = 1)");
        IntegridadeAuditoria semFim = integridade.verificar();
        assertThat(semFim.integra()).isFalse();
        assertThat(semFim.primeiraFalha().motivo()).isEqualTo("FIM_DIVERGENTE");
    }
}
