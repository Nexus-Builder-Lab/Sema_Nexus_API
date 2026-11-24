package br.com.nexusbuilderlab.semanexus.agentes;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fila da GPU (Issue #134): um {@link Semaphore} justo (ordem de chegada) limita quantas chamadas ao modelo rodam ao
 * mesmo tempo. Quem espera mais que o timeout curto desiste com {@link AgenteIndisponivelException} — o chamador cai
 * no modo demonstrativo (identificado), em vez de empilhar pedidos na GPU e estourar o tempo de todos.
 * Configuração: {@code AGENTES_GPU_CONCORRENCIA} (padrão 1, uma GPU) e {@code AGENTES_GPU_ESPERA_SEGUNDOS} (padrão 20).
 */
@Component
public class FilaGpu {

    public static final int CONCORRENCIA_PADRAO = 1;
    public static final int ESPERA_PADRAO_SEGUNDOS = 20;

    private final int vagas;
    private final Duration espera;
    private final Semaphore semaforo;

    @Autowired
    public FilaGpu(@Value("${AGENTES_GPU_CONCORRENCIA:" + CONCORRENCIA_PADRAO + "}") int vagas,
                   @Value("${AGENTES_GPU_ESPERA_SEGUNDOS:" + ESPERA_PADRAO_SEGUNDOS + "}") int esperaSegundos) {
        this(vagas, Duration.ofSeconds(Math.max(1, Math.min(esperaSegundos, 120))));
    }

    /** Construtor dos testes (espera em qualquer resolução). */
    public FilaGpu(int vagas, Duration espera) {
        this.vagas = Math.max(1, Math.min(vagas, 16));
        this.espera = espera.isNegative() ? Duration.ZERO : espera;
        this.semaforo = new Semaphore(this.vagas, true);
    }

    /**
     * Executa {@code chamada} ao conseguir vaga. Espera no máximo o menor entre o timeout da fila e
     * {@code esperaMaxima} (o que resta do orçamento da execução).
     *
     * @throws AgenteIndisponivelException fila cheia além do tempo de espera ou espera interrompida
     */
    public <T> T executar(Duration esperaMaxima, Supplier<T> chamada) {
        long esperaMs = Math.max(0, Math.min(espera.toMillis(), esperaMaxima.toMillis()));
        boolean obteve;
        try {
            obteve = semaforo.tryAcquire(esperaMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgenteIndisponivelException("Espera pela GPU interrompida.");
        }
        if (!obteve) {
            throw new AgenteIndisponivelException("GPU ocupada: fila de " + vagas + " vaga(s) sem liberação em "
                    + Math.max(1, (esperaMs + 999) / 1000) + " s.");
        }
        try {
            return chamada.get();
        } finally {
            semaforo.release();
        }
    }

    public int vagas() {
        return vagas;
    }

    public Duration espera() {
        return espera;
    }

    /** Chamadas ao modelo em andamento agora. */
    public int emUso() {
        return vagas - semaforo.availablePermits();
    }

    /** Pedidos aguardando vaga (estimativa do {@link Semaphore}). */
    public int aguardando() {
        return semaforo.getQueueLength();
    }
}
