package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoTextoAlterado;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Indexação vetorial dos documentos (Issue #141), sempre em segundo plano (uma thread própria, sem bloquear a subida
 * nem o upload):
 * <ul>
 *   <li>upload: depois do commit, o documento novo é dividido em trechos e indexado;</li>
 *   <li>preenchimento: ao iniciar e a cada {@code EMBEDDINGS_REINDEXAR_SEGUNDOS}, os documentos ativos ainda sem
 *   trechos do modelo configurado são indexados — idempotente (documento já indexado não é refeito) e resiliente
 *   (Ollama fora do ar = nova tentativa no próximo ciclo; a busca segue lexical enquanto isso).</li>
 * </ul>
 * Só grava a tabela derivada {@code DOCUMENTO_TRECHO}; nunca altera documentos.
 */
@Component
public class IndexadorTrechos {

    private static final Logger LOG = LoggerFactory.getLogger(IndexadorTrechos.class);
    static final int LOTE_EMBEDDINGS = 16;
    static final int LOTE_PENDENTES = 50;
    static final long ATRASO_INICIAL_SEGUNDOS = 15;

    private final ClienteEmbeddings embeddings;
    private final TrechoRepositorio trechos;
    private final EmbeddingsConfiguracao configuracao;
    private final Environment ambiente;
    private final ScheduledExecutorService executor;
    private final AtomicInteger indexados = new AtomicInteger();

    public IndexadorTrechos(ClienteEmbeddings embeddings, TrechoRepositorio trechos,
                            EmbeddingsConfiguracao configuracao, Environment ambiente) {
        this.embeddings = embeddings;
        this.trechos = trechos;
        this.configuracao = configuracao;
        this.ambiente = ambiente;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "indexacao-trechos");
            t.setDaemon(true);
            return t;
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        if (!configuracao.ativo() || ambiente.acceptsProfiles(Profiles.of("openapi"))) {
            return;
        }
        executor.scheduleWithFixedDelay(this::preencherSilencioso, ATRASO_INICIAL_SEGUNDOS,
                configuracao.reindexarSegundos(), TimeUnit.SECONDS);
    }

    /** Documento criado ou alterado: reindexa depois do commit (sem transação, roda direto). */
    @TransactionalEventListener(fallbackExecution = true)
    public void aoAlterar(DocumentoTextoAlterado evento) {
        if (!configuracao.ativo()) {
            return;
        }
        executor.execute(() -> {
            try {
                indexar(evento.documentoId());
            } catch (RuntimeException e) {
                // fica pendente: o preenchimento periódico tenta de novo
            }
        });
    }

    @PreDestroy
    void encerrar() {
        executor.shutdownNow();
    }

    private void preencherSilencioso() {
        try {
            preencher();
        } catch (RuntimeException e) {
            LOG.debug("Preenchimento de trechos adiado ({}).", e.getClass().getSimpleName());
        }
    }

    /**
     * Indexa os documentos ativos sem trechos do modelo. Para na primeira falha de embedding (tenta de novo no
     * próximo ciclo). Devolve quantos documentos foram indexados.
     */
    public int preencher() {
        if (!embeddings.disponivel()) {
            return 0;
        }
        int total = 0;
        java.util.Set<Long> vistos = new java.util.HashSet<>();
        while (true) {
            List<Long> pendentes = new ArrayList<>(trechos.pendentes(embeddings.modelo(), LOTE_PENDENTES));
            pendentes.removeIf(id -> !vistos.add(id)); // documento sem trecho possível não prende o laço
            if (pendentes.isEmpty()) {
                break;
            }
            for (Long id : pendentes) {
                if (!indexar(id)) {
                    return total;
                }
                total++;
            }
        }
        if (total > 0) {
            LOG.info("Busca híbrida: {} documento(s) indexado(s) com o modelo {}.", total, embeddings.modelo());
        }
        return total;
    }

    /** Divide o documento em trechos, gera os embeddings e substitui os trechos gravados. Falso se não indexou. */
    public boolean indexar(long documentoId) {
        var doc = trechos.documento(documentoId).orElse(null);
        if (doc == null) {
            return true; // excluído: nada a indexar (os trechos saem da busca pelo filtro de ativos)
        }
        List<String> partes = Fatiador.fatiar(doc.titulo(), doc.descricao(), doc.palavrasChave(), doc.texto());
        if (partes.isEmpty()) {
            return true;
        }
        List<String> entradas = new ArrayList<>(partes.size());
        for (int i = 0; i < partes.size(); i++) {
            // O título dá contexto aos trechos do corpo ("120 hectares" de qual documento?).
            entradas.add(i == 0 || doc.titulo() == null ? partes.get(i) : doc.titulo() + "\n" + partes.get(i));
        }
        List<float[]> vetores = new ArrayList<>(partes.size());
        try {
            for (int i = 0; i < entradas.size(); i += LOTE_EMBEDDINGS) {
                vetores.addAll(embeddings.gerar(entradas.subList(i, Math.min(entradas.size(), i + LOTE_EMBEDDINGS)),
                        ClienteEmbeddings.Finalidade.DOCUMENTO, false));
            }
        } catch (ClienteEmbeddings.EmbeddingsIndisponiveisException e) {
            return false;
        }
        trechos.substituir(documentoId, embeddings.modelo(), partes, vetores);
        indexados.incrementAndGet();
        return true;
    }

    /** Documentos indexados desde a subida (diagnóstico). */
    public int indexadosDesdeInicio() {
        return indexados.get();
    }
}
