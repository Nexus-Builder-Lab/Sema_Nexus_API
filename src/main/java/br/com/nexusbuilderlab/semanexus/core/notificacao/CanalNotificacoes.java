package br.com.nexusbuilderlab.semanexus.core.notificacao;

import br.com.nexusbuilderlab.semanexus.common.sse.RespostaSse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Entrega das notificações em tempo real por SSE (Issue #148), sobre a mesma base dos streams dos agentes
 * (Issue #135): cabeçalhos de {@link RespostaSse}, comentário {@code :keepalive} periódico e encerramento na primeira
 * falha de escrita.
 *
 * <ul>
 *   <li><b>Escopo:</b> cada stream pertence ao usuário autenticado que o abriu; uma notificação só é enviada aos
 *       streams do seu dono ({@code USUARIO_ID}). Não há parâmetro de usuário na requisição.</li>
 *   <li><b>Só depois do commit:</b> o evento {@link NotificacaoCriada} é ouvido em {@code AFTER_COMMIT}; notificação
 *       desfeita por rollback nunca é transmitida. O envio roda na thread do canal, não na da transação.</li>
 *   <li><b>Sessão limitada:</b> o stream dura no máximo {@code NOTIFICACOES_SSE_DURACAO_MINUTOS} (padrão 15) e nunca
 *       passa da expiração do JWT; o cliente reconecta e é autenticado de novo (perfil, situação ativa e revogação).
 *       O logout encerra na hora os streams abertos com o token revogado.</li>
 *   <li><b>Nó local (em memória):</b> o registro de streams ativos, os limites, o envio pós-commit e o encerramento
 *       imediato pelo logout ou por aba excedente ocorrem apenas na instância local da API (sem barramento entre
 *       nós).</li>
 *   <li><b>Limites:</b> até {@code NOTIFICACOES_SSE_MAX_POR_USUARIO} (padrão 3) streams por usuário — o mais antigo
 *       é fechado — e {@code NOTIFICACOES_SSE_MAX_SIMULTANEOS} (padrão 200) no total; além disso, 503
 *       {@code problem+json}.</li>
 * </ul>
 */
@Component
public class CanalNotificacoes implements DisposableBean {

    public static final String EVENTO_CONECTADO = "conectado";
    public static final String EVENTO_NOTIFICACAO = "notificacao";
    private static final Logger LOG = LoggerFactory.getLogger(CanalNotificacoes.class);

    private final Duration duracaoMaxima;
    private final long batimentoMs;
    private final int maxPorUsuario;
    private final int maxSimultaneos;
    private final Clock clock;
    private final ScheduledExecutorService agenda;
    private final Map<Long, List<Sessao>> porUsuario = new ConcurrentHashMap<>();
    private final AtomicInteger abertas = new AtomicInteger();

    @Autowired
    public CanalNotificacoes(@Value("${NOTIFICACOES_SSE_DURACAO_MINUTOS:15}") int duracaoMinutos,
                             @Value("${NOTIFICACOES_SSE_BATIMENTO_SEGUNDOS:15}") int batimentoSegundos,
                             @Value("${NOTIFICACOES_SSE_MAX_POR_USUARIO:3}") int maxPorUsuario,
                             @Value("${NOTIFICACOES_SSE_MAX_SIMULTANEOS:200}") int maxSimultaneos,
                             Clock clock) {
        this(Duration.ofMinutes(Math.max(1, Math.min(duracaoMinutos, 120))),
                Duration.ofSeconds(Math.max(1, Math.min(batimentoSegundos, 60))), maxPorUsuario, maxSimultaneos, clock);
    }

    /** Construtor dos testes (qualquer resolução de tempo). */
    public CanalNotificacoes(Duration duracaoMaxima, Duration batimento, int maxPorUsuario, int maxSimultaneos,
                             Clock clock) {
        this.duracaoMaxima = duracaoMaxima;
        this.batimentoMs = Math.max(10, batimento.toMillis());
        this.maxPorUsuario = Math.max(1, Math.min(maxPorUsuario, 10));
        this.maxSimultaneos = Math.max(1, Math.min(maxSimultaneos, 2000));
        this.clock = clock;
        this.agenda = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "notificacoes-sse");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Abre o stream do usuário. Primeiro evento: {@code conectado} com a contagem de não lidas.
     *
     * @param usuarioId dono do stream (do principal autenticado)
     * @param jti       identificador do token usado (logout encerra o stream); pode ser nulo
     * @param expiraEm  expiração do token (o stream não passa dela); pode ser nulo
     * @param naoLidas  contagem inicial de não lidas
     * @throws ResponseStatusException 503 quando há streams demais abertos
     */
    public ResponseEntity<SseEmitter> abrir(Long usuarioId, String jti, Instant expiraEm, long naoLidas) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        if (abertas.incrementAndGet() > maxSimultaneos) {
            abertas.decrementAndGet();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Muitas conexões de notificação abertas. As notificações seguem disponíveis pela consulta.");
        }
        SseEmitter emissor = new SseEmitter(timeoutMs(expiraEm));
        Sessao sessao = new Sessao(usuarioId, jti, emissor);
        emissor.onCompletion(() -> remover(sessao));
        emissor.onTimeout(() -> remover(sessao));
        emissor.onError(e -> remover(sessao));
        // compute é atômico por chave: não corre com a remoção da lista vazia em remover().
        List<Sessao> lista = porUsuario.compute(usuarioId, (k, atual) -> {
            List<Sessao> l = atual == null ? new CopyOnWriteArrayList<>() : atual;
            l.add(sessao);
            return l;
        });
        // Abas demais do mesmo usuário: fecha as mais antigas a partir de snapshot seguro.
        Sessao[] snapshot = lista.toArray(Sessao[]::new);
        int excesso = snapshot.length - maxPorUsuario;
        for (int i = 0; i < excesso; i++) {
            Sessao antiga = snapshot[i];
            antiga.fechar();
            remover(antiga);
        }
        sessao.enviar(EVENTO_CONECTADO, Map.of("naoLidas", naoLidas));
        sessao.batimento = agenda.scheduleAtFixedRate(sessao::batimento, batimentoMs, batimentoMs,
                TimeUnit.MILLISECONDS);
        if (sessao.encerrada.get()) {
            sessao.batimento.cancel(false);
        }
        return RespostaSse.de(emissor);
    }

    long timeoutMs(Instant expiraEm) {
        long maximo = duracaoMaxima.toMillis();
        if (expiraEm != null) {
            maximo = Math.min(maximo, Duration.between(clock.instant(), expiraEm).toMillis());
        }
        return Math.max(1000, maximo);
    }

    /** Entrega a notificação aos streams do dono, depois do commit (ou na hora, fora de transação). */
    @TransactionalEventListener(fallbackExecution = true)
    public void aoCriar(NotificacaoCriada evento) {
        List<Sessao> lista = porUsuario.get(evento.usuarioId());
        if (lista == null || lista.isEmpty()) {
            return;
        }
        List<Sessao> destino = new ArrayList<>(lista);
        try {
            agenda.execute(() -> destino.forEach(s -> s.enviar(EVENTO_NOTIFICACAO, evento.notificacao())));
        } catch (RejectedExecutionException e) {
            LOG.debug("Canal de notificações encerrando; notificação fica disponível pela consulta.");
        }
    }

    /** Encerra os streams abertos com o token informado (logout). Devolve quantos fechou. */
    public int encerrarDoToken(String jti) {
        if (jti == null) {
            return 0;
        }
        int n = 0;
        for (List<Sessao> lista : porUsuario.values()) {
            for (Sessao s : lista) {
                if (jti.equals(s.jti)) {
                    s.fechar();
                    remover(s);
                    n++;
                }
            }
        }
        return n;
    }

    /** Streams abertos (diagnóstico/testes). */
    public int abertas() {
        return abertas.get();
    }

    public int abertasDoUsuario(Long usuarioId) {
        List<Sessao> lista = porUsuario.get(usuarioId);
        return lista == null ? 0 : lista.size();
    }

    private void remover(Sessao sessao) {
        if (!sessao.removida.compareAndSet(false, true)) {
            return;
        }
        sessao.encerrar();
        abertas.decrementAndGet();
        porUsuario.computeIfPresent(sessao.usuarioId, (k, lista) -> {
            lista.remove(sessao);
            return lista.isEmpty() ? null : lista;
        });
    }

    @Override
    public void destroy() {
        agenda.shutdownNow();
        porUsuario.values().forEach(l -> l.forEach(Sessao::fechar));
    }

    /** Um stream aberto; qualquer falha de escrita = cliente foi embora. */
    final class Sessao {

        private final Long usuarioId;
        private final String jti;
        private final SseEmitter emissor;
        private final AtomicBoolean encerrada = new AtomicBoolean();
        private final AtomicBoolean removida = new AtomicBoolean();
        private volatile ScheduledFuture<?> batimento;

        Sessao(Long usuarioId, String jti, SseEmitter emissor) {
            this.usuarioId = usuarioId;
            this.jti = jti;
            this.emissor = emissor;
        }

        synchronized void enviar(String tipo, Object dados) {
            if (encerrada.get()) {
                return;
            }
            try {
                emissor.send(SseEmitter.event().name(tipo).data(dados, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                falhou(e);
            }
        }

        synchronized void batimento() {
            if (encerrada.get()) {
                return;
            }
            try {
                emissor.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException e) {
                falhou(e);
            }
        }

        private void falhou(Exception e) {
            boolean aberta = !encerrada.get();
            remover(this);
            if (aberta) {
                try {
                    emissor.completeWithError(e);
                } catch (RuntimeException ignorada) {
                    LOG.debug("Stream de notificações já encerrado ({})", ignorada.getClass().getSimpleName());
                }
            }
        }

        /** Para o batimento e marca como encerrada. */
        void encerrar() {
            encerrada.set(true);
            ScheduledFuture<?> b = batimento;
            if (b != null) {
                b.cancel(false);
            }
        }

        /** Fecha o stream pelo servidor (logout, aba excedente, desligamento). */
        void fechar() {
            boolean aberta = !encerrada.getAndSet(true);
            encerrar();
            if (aberta) {
                try {
                    emissor.complete();
                } catch (RuntimeException e) {
                    LOG.debug("Stream de notificações já encerrado ({})", e.getClass().getSimpleName());
                }
            }
        }
    }
}
