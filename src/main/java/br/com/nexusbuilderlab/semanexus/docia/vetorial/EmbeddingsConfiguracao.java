package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuração da busca híbrida do DocIA (Issue #141). Embeddings locais via Ollama (mesmo {@code OLLAMA_URL} dos
 * agentes), modelo aberto {@code nomic-embed-text} (Apache 2.0, 768 dimensões). Tudo opcional: sem o modelo ou com
 * {@code EMBEDDINGS_ATIVO=false} a busca continua só lexical, sem falha.
 * <ul>
 *   <li>{@code EMBEDDINGS_ATIVO} (padrão <b>false</b> desde o benchmark real de 24/11/2026: a híbrida não superou a
 *   textual no acervo demonstrativo — ver docs/AGENTES_IA.md) — liga a indexação e a parte vetorial da busca;</li>
 *   <li>{@code EMBEDDINGS_MODELO} (padrão nomic-embed-text) — precisa gerar vetores de {@value #DIMENSAO} dimensões
 *   (a coluna é {@code VECTOR(768, FLOAT32)}); outra dimensão desliga a parte vetorial com aviso no log;</li>
 *   <li>{@code BUSCA_RRF_K} (padrão 60) — constante da fusão RRF;</li>
 *   <li>{@code EMBEDDINGS_SIMILARIDADE_MINIMA} (padrão 0.65) — documento que só a busca vetorial encontrou entra
 *   apenas com similaridade de cosseno igual ou acima dela (preserva a abstenção);</li>
 *   <li>{@code EMBEDDINGS_REINDEXAR_SEGUNDOS} (padrão 120) — intervalo da verificação de documentos sem trechos.</li>
 * </ul>
 */
@Component
public class EmbeddingsConfiguracao {

    public static final int DIMENSAO = 768;
    public static final String MODELO_PADRAO = "nomic-embed-text";
    public static final int RRF_K_PADRAO = 60;
    public static final double SIMILARIDADE_MINIMA_PADRAO = 0.65;

    private static final Pattern FORMATO_MODELO = Pattern.compile("^[a-z0-9][a-z0-9._/-]{1,60}(:[a-z0-9._-]{1,40})?$");
    private static final Pattern FORMATO_URL = Pattern.compile("^https?://[A-Za-z0-9.-]+(:\\d{1,5})?/?$");

    private final boolean ativo;
    private final String modelo;
    private final String ollamaUrl;
    private final int rrfK;
    private final double similaridadeMinima;
    private final int reindexarSegundos;

    @Autowired
    public EmbeddingsConfiguracao(@Value("${EMBEDDINGS_ATIVO:false}") boolean ativo,
                                  @Value("${EMBEDDINGS_MODELO:" + MODELO_PADRAO + "}") String modelo,
                                  @Value("${OLLAMA_URL:http://ollama:11434}") String ollamaUrl,
                                  @Value("${BUSCA_RRF_K:" + RRF_K_PADRAO + "}") int rrfK,
                                  @Value("${EMBEDDINGS_SIMILARIDADE_MINIMA:" + SIMILARIDADE_MINIMA_PADRAO + "}")
                                  double similaridadeMinima,
                                  @Value("${EMBEDDINGS_REINDEXAR_SEGUNDOS:120}") int reindexarSegundos) {
        String m = modelo == null ? "" : modelo.strip();
        this.modelo = FORMATO_MODELO.matcher(m).matches() ? m : MODELO_PADRAO;
        String u = ollamaUrl == null ? "" : ollamaUrl.strip();
        this.ollamaUrl = (FORMATO_URL.matcher(u).matches() ? u : "http://ollama:11434").replaceAll("/$", "");
        this.ativo = ativo;
        this.rrfK = Math.max(1, Math.min(rrfK, 1000));
        this.similaridadeMinima = Double.isNaN(similaridadeMinima) ? SIMILARIDADE_MINIMA_PADRAO
                : Math.max(0, Math.min(similaridadeMinima, 1));
        this.reindexarSegundos = Math.max(10, Math.min(reindexarSegundos, 86_400));
    }

    /** Configuração padrão (testes). */
    public static EmbeddingsConfiguracao padrao() {
        return new EmbeddingsConfiguracao(true, MODELO_PADRAO, "http://ollama:11434", RRF_K_PADRAO,
                SIMILARIDADE_MINIMA_PADRAO, 120);
    }

    public boolean ativo() {
        return ativo;
    }

    public String modelo() {
        return modelo;
    }

    public String ollamaUrl() {
        return ollamaUrl;
    }

    public int rrfK() {
        return rrfK;
    }

    public double similaridadeMinima() {
        return similaridadeMinima;
    }

    public int reindexarSegundos() {
        return reindexarSegundos;
    }

    @Override
    public String toString() {
        return "EmbeddingsConfiguracao{ativo=" + ativo + ", modelo=" + modelo + ", ollamaUrl=" + ollamaUrl
                + ", rrfK=" + rrfK + ", similaridadeMinima=" + similaridadeMinima + "}";
    }
}
