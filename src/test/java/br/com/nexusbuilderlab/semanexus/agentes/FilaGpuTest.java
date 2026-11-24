package br.com.nexusbuilderlab.semanexus.agentes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Fila da GPU (Issue #134): semáforo justo, timeout curto e queda no modo demonstrativo quando ocupada. */
class FilaGpuTest {

    private final ExecutorService threads = Executors.newFixedThreadPool(4);

    @AfterEach
    void encerrar() {
        threads.shutdownNow();
    }

    /** Ocupa a única vaga da fila até {@code liberar} ser acionado. */
    private Future<String> ocupar(FilaGpu fila, CountDownLatch ocupada, CountDownLatch liberar) {
        return threads.submit(() -> fila.executar(Duration.ofSeconds(5), () -> {
            ocupada.countDown();
            try {
                liberar.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "primeira";
        }));
    }

    @Test
    void segundaChamadaDesisteNoTimeoutCurtoQuandoAGpuEstaOcupada() throws Exception {
        FilaGpu fila = new FilaGpu(1, Duration.ofMillis(150));
        CountDownLatch ocupada = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        Future<String> primeira = ocupar(fila, ocupada, liberar);
        assertThat(ocupada.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(fila.emUso()).isEqualTo(1);

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> fila.executar(Duration.ofSeconds(30), () -> "segunda"))
                .isInstanceOf(AgenteIndisponivelException.class)
                .hasMessageContaining("GPU ocupada");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(3));

        liberar.countDown();
        assertThat(primeira.get(5, TimeUnit.SECONDS)).isEqualTo("primeira");
        assertThat(fila.emUso()).isZero();
        // Liberada a vaga, a próxima chamada roda normalmente.
        assertThat(fila.executar(Duration.ofSeconds(1), () -> "terceira")).isEqualTo("terceira");
    }

    @Test
    void chamadaEmEsperaRodaAssimQueAVagaLibera() throws Exception {
        FilaGpu fila = new FilaGpu(1, Duration.ofSeconds(5));
        CountDownLatch ocupada = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ocupar(fila, ocupada, liberar);
        assertThat(ocupada.await(5, TimeUnit.SECONDS)).isTrue();

        Future<String> esperando = threads.submit(() -> fila.executar(Duration.ofSeconds(5), () -> "segunda"));
        long limite = System.currentTimeMillis() + 3000;
        while (fila.aguardando() == 0 && System.currentTimeMillis() < limite) {
            Thread.onSpinWait();
        }
        assertThat(fila.aguardando()).isEqualTo(1);
        liberar.countDown();
        assertThat(esperando.get(5, TimeUnit.SECONDS)).isEqualTo("segunda");
    }

    @Test
    void esperaNaoPassaDoOrcamentoRestante() {
        FilaGpu fila = new FilaGpu(1, Duration.ofSeconds(30));
        CountDownLatch ocupada = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ocupar(fila, ocupada, liberar);
        try {
            assertThat(ocupada.await(5, TimeUnit.SECONDS)).isTrue();
            long inicio = System.nanoTime();
            assertThatThrownBy(() -> fila.executar(Duration.ofMillis(100), () -> "x"))
                    .isInstanceOf(AgenteIndisponivelException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(3));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            liberar.countDown();
        }
    }

    @Test
    void configuracaoTemLimitesSeguros() {
        FilaGpu fila = new FilaGpu(0, 0);
        assertThat(fila.vagas()).isEqualTo(1);
        assertThat(fila.espera()).isEqualTo(Duration.ofSeconds(1));
        assertThat(new FilaGpu(99, 999).vagas()).isEqualTo(16);
        assertThat(new FilaGpu(99, 999).espera()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void executorComGpuOcupadaCaiNoModoDemonstrativoEAudita() throws Exception {
        FilaGpu fila = new FilaGpu(1, Duration.ofMillis(100));
        AuditService auditoria = mock(AuditService.class);
        List<PedidosContados> nada = new ArrayList<>();
        ClienteModelo cliente = new ClienteModelo() {
            @Override
            public boolean disponivel() {
                return true;
            }

            @Override
            public String modelo(String modeloSugerido) {
                return "modelo-local";
            }

            @Override
            public RespostaModelo criar(PedidoModelo pedido) {
                nada.add(new PedidosContados());
                return new RespostaModelo("stop", List.of(), "{\"ok\":true}", null, 1, 1, 0);
            }
        };
        AgentesConfiguracao config = new AgentesConfiguracao("ollama", "", "claude-haiku-5-5", "claude-sonnet-5-5",
                "http://ollama:11434", "qwen3:4b", false, 30, 3);
        ExecutorAgente executor = new ExecutorAgente(cliente, config, auditoria, new ObjectMapper(),
                new MetricasAgentes(), fila);
        DefinicaoAgente agente = new DefinicaoAgente("teste", "claude-haiku-5-5", "Instruções.", List.of(),
                Map.of("type", "object"), "low", 500, 1);

        CountDownLatch ocupada = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        ocupar(fila, ocupada, liberar);
        assertThat(ocupada.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            assertThatThrownBy(() -> executor.executar(agente, "Tarefa", "7"))
                    .isInstanceOf(AgenteIndisponivelException.class)
                    .hasMessageContaining("GPU ocupada");
            assertThat(nada).isEmpty();
            verify(auditoria).registrar(eq("AGENTE_FALHA"), eq("AGENTE"), eq("teste:7"), contains("GPU ocupada"));
        } finally {
            liberar.countDown();
        }
        assertThat(executor.executar(agente, "Tarefa", "8").resposta().get("ok").asBoolean()).isTrue();
    }

    @Test
    void aquecimentoDoModeloOcupaUmaVagaDaFila() {
        FilaGpu fila = new FilaGpu(1, Duration.ofMillis(100));
        int[] emUsoDuranteAquecimento = {-1};
        ClienteModelo cliente = new ClienteModelo() {
            @Override
            public boolean disponivel() {
                return true;
            }

            @Override
            public String modelo(String modeloSugerido) {
                return "modelo-local";
            }

            @Override
            public boolean aquecer() {
                emUsoDuranteAquecimento[0] = fila.emUso();
                return true;
            }

            @Override
            public RespostaModelo criar(PedidoModelo pedido) {
                throw new UnsupportedOperationException();
            }
        };
        AgentesConfiguracao config = new AgentesConfiguracao("ollama", "", "claude-haiku-5-5", "claude-sonnet-5-5",
                "http://ollama:11434", "qwen3:4b", false, 30, 3);

        new AquecimentoModelo(cliente, config, fila).aquecer();

        assertThat(emUsoDuranteAquecimento[0]).isEqualTo(1);
        assertThat(fila.emUso()).isZero();
    }

    private record PedidosContados() {
    }
}
