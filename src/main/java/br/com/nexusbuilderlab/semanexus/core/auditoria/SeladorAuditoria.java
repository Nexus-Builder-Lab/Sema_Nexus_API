package br.com.nexusbuilderlab.semanexus.core.auditoria;

import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoria.Registro;
import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoriaRepositorio.Estado;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sela os registros de auditoria na cadeia SHA-256 (Issue #148).
 *
 * <p><b>Concorrência.</b> O registro de auditoria é inserido na transação de quem chama (some junto num rollback) e
 * selado depois do commit, numa transação curta e própria que trava a linha única de {@code AUDITORIA_CADEIA}
 * ({@code SELECT ... FOR UPDATE}). Assim, inserções simultâneas — de threads ou de instâncias diferentes — nunca leem
 * o mesmo "último hash": a cadeia segue a ordem de selagem ({@code SEQ_CADEIA}), não a ordem dos IDs. Como a trava
 * não é tomada dentro das transações de negócio, ela não cria deadlock com as linhas que elas alteram.
 *
 * <p>Os pedidos de selagem são agrupados numa única thread por instância; se uma selagem falhar, os registros ficam
 * pendentes e entram na próxima (inclusive na rodada periódica de segurança, a cada minuto).
 */
@Component
public class SeladorAuditoria implements DisposableBean {

    static final int LOTE = 500;
    private static final Logger LOG = LoggerFactory.getLogger(SeladorAuditoria.class);

    private final CadeiaAuditoriaRepositorio cadeia;
    private final TransactionTemplate novaTransacao;
    private final ExecutorService executor;
    private final AtomicBoolean agendado = new AtomicBoolean();

    public SeladorAuditoria(CadeiaAuditoriaRepositorio cadeia, PlatformTransactionManager transacoes) {
        this.cadeia = cadeia;
        this.novaTransacao = new TransactionTemplate(transacoes);
        this.novaTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "auditoria-selador");
            t.setDaemon(true);
            return t;
        });
    }

    /** Pede uma selagem assíncrona; pedidos feitos enquanto outro aguarda na fila são agrupados. */
    public void solicitarSelagem() {
        if (!agendado.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::rodar);
        } catch (RejectedExecutionException e) {
            agendado.set(false);
            LOG.debug("Selagem da auditoria não agendada (aplicação encerrando).");
        }
    }

    private void rodar() {
        // Liberado antes de selar: um pedido que chegue durante a selagem agenda outra rodada (pega o que for
        // commitado depois da consulta desta).
        agendado.set(false);
        try {
            selarPendentes();
        } catch (RuntimeException e) {
            LOG.warn("Selagem da auditoria falhou; os registros seguem pendentes para a próxima rodada: {}",
                    e.getClass().getSimpleName());
        }
    }

    /** Rodada de segurança: sela o que tiver ficado pendente (falha anterior, reinício da API). */
    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT60S")
    public void rodadaPeriodica() {
        solicitarSelagem();
    }

    /** Sela, de forma síncrona, todos os registros pendentes. Devolve quantos selou. */
    public int selarPendentes() {
        int total = 0;
        int lote;
        do {
            Integer n = novaTransacao.execute(s -> selarLote());
            lote = n == null ? 0 : n;
            total += lote;
        } while (lote == LOTE);
        return total;
    }

    private int selarLote() {
        Estado estado = cadeia.travarEstado();
        List<Registro> pendentes = cadeia.pendentes(estado.inicioId(), LOTE);
        if (pendentes.isEmpty()) {
            return 0;
        }
        long seq = estado.ultimaSeq();
        String anterior = estado.ultimaSeq() == 0 ? CadeiaAuditoria.GENESE : estado.ultimoHash();
        int selados = 0;
        for (Registro r : pendentes) {
            String hash = CadeiaAuditoria.hash(seq + 1, anterior, r);
            if (cadeia.selar(r.id(), seq + 1, anterior, hash)) {
                seq++;
                anterior = hash;
                selados++;
            }
        }
        if (selados > 0) {
            cadeia.atualizarEstado(seq, anterior);
        }
        return pendentes.size();
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
