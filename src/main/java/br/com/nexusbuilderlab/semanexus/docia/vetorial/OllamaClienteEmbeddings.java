package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Embeddings pelo Ollama local ({@code POST /api/embed}), sem custo e sem os dados saírem do servidor (Issue #141).
 * Prefixos do nomic-embed-text: {@code search_query:} e {@code search_document:}. Depois de uma falha, novas chamadas
 * são recusadas por {@link #PAUSA_APOS_FALHA} (a busca não espera o timeout a cada pergunta) e a busca fica lexical.
 * Não usa a fila da GPU dos agentes: o modelo de embedding é pequeno (~0,3 GB) e cada chamada leva milissegundos.
 */
@Component
public class OllamaClienteEmbeddings implements ClienteEmbeddings {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaClienteEmbeddings.class);
    static final Duration PAUSA_APOS_FALHA = Duration.ofSeconds(30);
    static final Duration TIMEOUT_INTERATIVO = Duration.ofSeconds(4);
    static final Duration TIMEOUT_INDEXACAO = Duration.ofSeconds(90);

    private final EmbeddingsConfiguracao configuracao;
    private final RestClient interativo;
    private final RestClient indexacao;
    private volatile long pausadoAte;
    private volatile boolean ultimaFalhaRegistrada;

    @Autowired
    public OllamaClienteEmbeddings(EmbeddingsConfiguracao configuracao) {
        this(configuracao, http(configuracao, TIMEOUT_INTERATIVO), http(configuracao, TIMEOUT_INDEXACAO));
    }

    OllamaClienteEmbeddings(EmbeddingsConfiguracao configuracao, RestClient interativo, RestClient indexacao) {
        this.configuracao = configuracao;
        this.interativo = interativo;
        this.indexacao = indexacao;
    }

    private static RestClient http(EmbeddingsConfiguracao c, Duration leitura) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(2));
        f.setReadTimeout(leitura);
        return RestClient.builder().baseUrl(c.ollamaUrl()).requestFactory(f).build();
    }

    @Override
    public String modelo() {
        return configuracao.modelo();
    }

    @Override
    public boolean disponivel() {
        return configuracao.ativo() && System.currentTimeMillis() >= pausadoAte;
    }

    @Override
    public List<float[]> gerar(List<String> textos, Finalidade finalidade, boolean interativa) {
        if (!configuracao.ativo()) {
            throw new EmbeddingsIndisponiveisException("Embeddings desligados (EMBEDDINGS_ATIVO=false).");
        }
        if (!disponivel()) {
            throw new EmbeddingsIndisponiveisException("Embeddings em pausa após falha recente.");
        }
        if (textos.isEmpty()) {
            return List.of();
        }
        String prefixo = finalidade == Finalidade.CONSULTA ? "search_query: " : "search_document: ";
        List<String> entrada = textos.stream().map(t -> prefixo + t).toList();
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("model", configuracao.modelo());
        corpo.put("input", entrada);
        corpo.put("truncate", true);
        try {
            JsonNode r = (interativa ? interativo : indexacao).post().uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve().body(JsonNode.class);
            List<float[]> vetores = ler(r, textos.size());
            if (ultimaFalhaRegistrada) {
                LOG.info("Embeddings disponíveis novamente (modelo {}).", configuracao.modelo());
                ultimaFalhaRegistrada = false;
            }
            return vetores;
        } catch (EmbeddingsIndisponiveisException e) {
            falhou(e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            // Só a classe da exceção: a mensagem pode trazer o corpo da resposta.
            falhou("falha ao chamar o Ollama (" + e.getClass().getSimpleName() + ")");
            throw new EmbeddingsIndisponiveisException("Embeddings indisponíveis.");
        }
    }

    private void falhou(String motivo) {
        pausadoAte = System.currentTimeMillis() + PAUSA_APOS_FALHA.toMillis();
        if (!ultimaFalhaRegistrada) {
            LOG.info("Embeddings indisponíveis (modelo {}): {}. A busca segue só lexical.", configuracao.modelo(),
                    motivo);
            ultimaFalhaRegistrada = true;
        }
    }

    static List<float[]> ler(JsonNode r, int esperados) {
        JsonNode lista = r == null ? null : r.path("embeddings");
        if (lista == null || !lista.isArray() || lista.size() != esperados) {
            throw new EmbeddingsIndisponiveisException("resposta sem a quantidade esperada de embeddings");
        }
        List<float[]> vetores = new ArrayList<>(esperados);
        for (JsonNode v : lista) {
            if (!v.isArray() || v.size() != EmbeddingsConfiguracao.DIMENSAO) {
                throw new EmbeddingsIndisponiveisException("o modelo não gera vetores de "
                        + EmbeddingsConfiguracao.DIMENSAO + " dimensões");
            }
            float[] f = new float[v.size()];
            for (int i = 0; i < f.length; i++) {
                f[i] = (float) v.get(i).asDouble();
            }
            vetores.add(Vetores.normalizar(f));
        }
        return vetores;
    }
}
