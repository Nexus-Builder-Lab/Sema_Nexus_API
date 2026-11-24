package br.com.nexusbuilderlab.semanexus.agentes;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Transmissão SSE da execução de um agente (Issue #135). Roda o mesmo trabalho do endpoint JSON numa thread própria
 * (com o contexto de segurança do usuário, para escopo e auditoria iguais) e entrega os eventos tipados de
 * {@link EventoAgente}: {@code inicio} → {@code ferramenta_iniciada}/{@code ferramenta_concluida}… →
 * ({@code fallback_demonstrativo}) → {@code delta_texto} → {@code concluido} (mesmo JSON do endpoint síncrono), ou
 * {@code erro} (mensagem pública + referência; detalhe só no log).
 * <ul>
 *   <li>Timeout do {@link SseEmitter} = orçamento + timeout por chamada + margem (sempre ≥ orçamento).</li>
 *   <li>Comentário {@code :keepalive} a cada {@code AGENTES_SSE_BATIMENTO_SEGUNDOS} (padrão 15 s), abaixo do
 *       {@code proxy_read_timeout} do Nginx do Web; também revela desconexões.</li>
 *   <li>Cliente desconectado ou timeout: a execução para na próxima etapa e desiste da fila da GPU (vaga liberada).</li>
 *   <li>{@code X-Accel-Buffering: no} e {@code Cache-Control: no-cache, no-transform}: o Nginx não acumula eventos.</li>
 *   <li>No máximo {@code AGENTES_SSE_MAX_SIMULTANEOS} (padrão 16) transmissões ao mesmo tempo; além disso, 503.</li>
 * </ul>
 */
@Component
public class TransmissaoAgente implements DisposableBean {

    static final String MENSAGEM_ERRO = "Falha interna ao executar o agente. Tente novamente ou use a chamada padrão.";
    static final Duration MARGEM_TIMEOUT = Duration.ofSeconds(30);
    private static final Logger LOG = LoggerFactory.getLogger(TransmissaoAgente.class);

    private final AgentesConfiguracao configuracao;
    private final long batimentoMs;
    private final ThreadPoolExecutor trabalhadores;
    private final ScheduledExecutorService batimentos;

    @Autowired
    public TransmissaoAgente(AgentesConfiguracao configuracao,
                             @Value("${AGENTES_SSE_BATIMENTO_SEGUNDOS:15}") int batimentoSegundos,
                             @Value("${AGENTES_SSE_MAX_SIMULTANEOS:16}") int maxSimultaneos) {
        this(configuracao, Duration.ofSeconds(Math.max(1, Math.min(batimentoSegundos, 60))), maxSimultaneos);
    }

    /** Construtor dos testes (batimento em qualquer resolução). */
    public TransmissaoAgente(AgentesConfiguracao configuracao, Duration batimento, int maxSimultaneos) {
        this.configuracao = configuracao;
        this.batimentoMs = Math.max(10, batimento.toMillis());
        AtomicInteger n = new AtomicInteger();
        this.trabalhadores = new ThreadPoolExecutor(0, Math.max(1, Math.min(maxSimultaneos, 64)), 60,
                TimeUnit.SECONDS, new SynchronousQueue<>(), r -> {
                    Thread t = new Thread(r, "agente-sse-" + n.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
        this.batimentos = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agente-sse-batimento");
            t.setDaemon(true);
            return t;
        });
    }

    /** Timeout do emissor: nunca menor que o orçamento da execução. */
    public long timeoutMs() {
        return configuracao.orcamento().plus(configuracao.timeout()).plus(MARGEM_TIMEOUT).toMillis();
    }

    /**
     * Inicia a transmissão. Validações que devem virar status HTTP (404/403/409) precisam ser feitas antes, na thread
     * da requisição.
     *
     * @param agente     nome do agente (evento {@code inicio})
     * @param trabalho   o mesmo trabalho do endpoint JSON, recebendo o observador
     * @param textoFinal texto exibível da resposta final, enviado num único {@code delta_texto} (pode ser nulo)
     * @throws ResponseStatusException 503 quando já há transmissões demais em andamento
     */
    public <T> ResponseEntity<SseEmitter> transmitir(String agente, Function<ObservadorAgente, T> trabalho,
                                                     Function<T, String> textoFinal) {
        SseEmitter emissor = new SseEmitter(timeoutMs());
        Sessao sessao = new Sessao(emissor);
        emissor.onCompletion(sessao::encerrar);
        emissor.onTimeout(sessao::encerrar);
        emissor.onError(e -> sessao.encerrar());
        sessao.emitir(EventoAgente.inicio(agente));
        Runnable tarefa = new DelegatingSecurityContextRunnable(() -> executar(sessao, trabalho, textoFinal));
        try {
            trabalhadores.execute(tarefa);
        } catch (RejectedExecutionException e) {
            sessao.encerrar();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Muitas execuções de agente em andamento. Tente novamente em instantes.");
        }
        sessao.agendarBatimento(batimentos, batimentoMs);
        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .cacheControl(CacheControl.noCache().noTransform())
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emissor);
    }

    private <T> void executar(Sessao sessao, Function<ObservadorAgente, T> trabalho, Function<T, String> textoFinal) {
        try {
            T resultado = trabalho.apply(sessao);
            if (sessao.cancelado()) {
                return;
            }
            String texto = textoFinal == null ? null : textoFinal.apply(resultado);
            if (texto != null && !texto.isBlank()) {
                sessao.emitir(EventoAgente.deltaTexto(texto));
            }
            sessao.enviar(EventoAgente.CONCLUIDO, resultado);
        } catch (ExecucaoCanceladaException e) {
            LOG.debug("Transmissão do agente cancelada pelo cliente.");
        } catch (RuntimeException e) {
            // Mesmo padrão do catálogo de erros: o cliente recebe mensagem pública + referência; o detalhe vai ao log.
            String referencia = ErroFerramenta.novaReferencia();
            LOG.warn("Transmissão do agente falhou [{}]: {} - {}", referencia, e.getClass().getName(), e.getMessage());
            sessao.enviar(EventoAgente.ERRO, Map.of("mensagem", MENSAGEM_ERRO, "referencia", referencia));
        } finally {
            sessao.concluir();
        }
    }

    /** Transmissões em andamento (diagnóstico/testes). */
    public int emAndamento() {
        return trabalhadores.getActiveCount();
    }

    @Override
    public void destroy() {
        trabalhadores.shutdownNow();
        batimentos.shutdownNow();
    }

    /** Observador ligado ao emissor; qualquer falha de envio marca o cliente como desconectado. */
    static final class Sessao implements ObservadorAgente {

        private final SseEmitter emissor;
        private final AtomicBoolean encerrado = new AtomicBoolean();
        private volatile ScheduledFuture<?> batimento;

        Sessao(SseEmitter emissor) {
            this.emissor = emissor;
        }

        @Override
        public void emitir(EventoAgente evento) {
            enviar(evento.tipo(), evento.dados());
        }

        void enviar(String tipo, Object dados) {
            if (encerrado.get()) {
                return;
            }
            try {
                emissor.send(SseEmitter.event().name(tipo).data(dados, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                encerrar();
            }
        }

        void agendarBatimento(ScheduledExecutorService agenda, long intervaloMs) {
            batimento = agenda.scheduleAtFixedRate(this::batimento, intervaloMs, intervaloMs, TimeUnit.MILLISECONDS);
            if (encerrado.get()) {
                batimento.cancel(false); // terminou antes do agendamento
            }
        }

        void batimento() {
            if (encerrado.get()) {
                return;
            }
            try {
                emissor.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException e) {
                encerrar();
            }
        }

        @Override
        public boolean cancelado() {
            return encerrado.get();
        }

        /** Cliente desconectou, timeout ou fim normal: para o batimento e sinaliza o cancelamento ao agente. */
        void encerrar() {
            encerrado.set(true);
            ScheduledFuture<?> b = batimento;
            if (b != null) {
                b.cancel(false);
            }
        }

        /** Fim do trabalho: fecha o stream (se o cliente ainda estiver lá). */
        void concluir() {
            boolean aberto = !encerrado.getAndSet(true);
            ScheduledFuture<?> b = batimento;
            if (b != null) {
                b.cancel(false);
            }
            if (aberto) {
                try {
                    emissor.complete();
                } catch (RuntimeException e) {
                    LOG.debug("Stream já encerrado ({})", e.getClass().getSimpleName());
                }
            }
        }
    }
}
