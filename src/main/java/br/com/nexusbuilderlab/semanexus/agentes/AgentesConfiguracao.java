package br.com.nexusbuilderlab.semanexus.agentes;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuração dos agentes, só por variáveis de ambiente.
 * <ul>
 *   <li>{@code AGENTES_PROVEDOR=ollama} (padrão): modelo aberto local via Ollama — sem custo e sem enviar dados para
 *   fora da máquina;</li>
 *   <li>{@code anthropic}: Claude, exige {@code ANTHROPIC_API_KEY} com créditos (opcional, para uso futuro);</li>
 *   <li>{@code demo}: só regras locais.</li>
 * </ul>
 * A chave da Anthropic nunca é exposta: não há getter público e {@link #toString()} não a inclui.
 */
@Component
public class AgentesConfiguracao {

    public enum Provedor { OLLAMA, ANTHROPIC, DEMO }

    /** Modelo aberto padrão: Qwen3-4B-Instruct-2507 (Apache 2.0, ferramentas, sem modo de raciocínio, ~2,5 GB). */
    public static final String MODELO_LOCAL_PADRAO = "qwen3:4b-instruct";
    public static final String MODELO_TRIAGEM_PADRAO = "claude-haiku-5-5";
    public static final String MODELO_DOCIA_PADRAO = "claude-sonnet-5-5";

    private static final Pattern FORMATO_CHAVE = Pattern.compile("^sk-ant-[A-Za-z0-9_-]{20,}$");
    private static final Pattern FORMATO_MODELO_CLAUDE = Pattern.compile("^claude-[a-z0-9.-]{3,60}$");
    private static final Pattern FORMATO_MODELO_LOCAL = Pattern.compile("^[a-z0-9][a-z0-9._-]{1,60}(:[a-z0-9._-]{1,40})?$");
    private static final Pattern FORMATO_URL = Pattern.compile("^https?://[A-Za-z0-9.-]+(:\\d{1,5})?/?$");

    private final Provedor provedor;
    private final String chave;
    private final String modeloTriagem;
    private final String modeloDocia;
    private final String ollamaUrl;
    private final String modeloLocal;
    private final boolean pensar;
    private final Duration timeout;
    private final int maxIteracoes;

    public AgentesConfiguracao(@Value("${AGENTES_PROVEDOR:ollama}") String provedor,
                               @Value("${ANTHROPIC_API_KEY:}") String chave,
                               @Value("${AGENTE_TRIAGEM_MODELO:" + MODELO_TRIAGEM_PADRAO + "}") String modeloTriagem,
                               @Value("${AGENTE_DOCIA_MODELO:" + MODELO_DOCIA_PADRAO + "}") String modeloDocia,
                               @Value("${OLLAMA_URL:http://ollama:11434}") String ollamaUrl,
                               @Value("${OLLAMA_MODELO:" + MODELO_LOCAL_PADRAO + "}") String modeloLocal,
                               @Value("${OLLAMA_PENSAR:false}") boolean pensar,
                               @Value("${AGENTES_TIMEOUT_SEGUNDOS:90}") int timeoutSegundos,
                               @Value("${AGENTES_MAX_ITERACOES:6}") int maxIteracoes) {
        this.provedor = provedor(provedor);
        this.chave = chave == null ? "" : chave.strip();
        this.modeloTriagem = valido(modeloTriagem, FORMATO_MODELO_CLAUDE, MODELO_TRIAGEM_PADRAO);
        this.modeloDocia = valido(modeloDocia, FORMATO_MODELO_CLAUDE, MODELO_DOCIA_PADRAO);
        this.ollamaUrl = valido(ollamaUrl, FORMATO_URL, "http://ollama:11434").replaceAll("/$", "");
        this.modeloLocal = valido(modeloLocal, FORMATO_MODELO_LOCAL, MODELO_LOCAL_PADRAO);
        this.pensar = pensar;
        this.timeout = Duration.ofSeconds(Math.max(5, Math.min(timeoutSegundos, 300)));
        this.maxIteracoes = Math.max(1, Math.min(maxIteracoes, 10));
    }

    public Provedor provedor() {
        return provedor;
    }

    /** true quando há chave da Anthropic com formato válido (só relevante com {@code AGENTES_PROVEDOR=anthropic}). */
    public boolean chaveAnthropicValida() {
        return FORMATO_CHAVE.matcher(chave).matches();
    }

    /** Uso exclusivo da fábrica do cliente Anthropic; nunca registrar nem devolver em resposta. */
    String chave() {
        return chave;
    }

    public String modeloTriagem() {
        return modeloTriagem;
    }

    public String modeloDocia() {
        return modeloDocia;
    }

    public String ollamaUrl() {
        return ollamaUrl;
    }

    public String modeloLocal() {
        return modeloLocal;
    }

    /** Envia {@code think=true} ao Ollama (só para modelos com modo de raciocínio; os demais recusam o campo). */
    public boolean pensar() {
        return pensar;
    }

    public Duration timeout() {
        return timeout;
    }

    public int maxIteracoes() {
        return maxIteracoes;
    }

    private static Provedor provedor(String valor) {
        String v = valor == null ? "" : valor.strip().toUpperCase(Locale.ROOT);
        for (Provedor p : Provedor.values()) {
            if (p.name().equals(v)) {
                return p;
            }
        }
        return Provedor.OLLAMA;
    }

    private static String valido(String valor, Pattern formato, String padrao) {
        String v = valor == null ? "" : valor.strip();
        return formato.matcher(v).matches() ? v : padrao;
    }

    @Override
    public String toString() {
        return "AgentesConfiguracao[provedor=" + provedor + ", chaveAnthropicValida=" + chaveAnthropicValida()
                + ", ollamaUrl=" + ollamaUrl + ", modeloLocal=" + modeloLocal + ", modeloTriagem=" + modeloTriagem
                + ", modeloDocia=" + modeloDocia + ", timeout=" + timeout + ", maxIteracoes=" + maxIteracoes + "]";
    }
}
