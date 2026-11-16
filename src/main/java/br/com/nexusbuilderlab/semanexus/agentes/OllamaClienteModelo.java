package br.com.nexusbuilderlab.semanexus.agentes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Provedor local (padrão): modelo aberto servido pelo Ollama na própria máquina (serviço {@code ollama} do compose,
 * com GPU quando disponível). Sem custo e sem enviar dados para fora. Usa {@code POST /api/chat} com ferramentas;
 * quando o modelo termina, uma chamada final com {@code format} = esquema JSON garante a resposta estruturada
 * (modelos pequenos às vezes respondem em prosa). Agentes que aceitam texto livre (DocIA) recebem a prosa direto,
 * sem a reescrita em JSON. O modelo é aquecido ao iniciar a API e fica na memória pelo tempo configurado
 * ({@code OLLAMA_MANTER_CARREGADO}).
 */
public class OllamaClienteModelo implements ClienteModelo {

    static final String PEDIDO_JSON = "Responda agora somente com o objeto JSON final, no formato pedido, "
            + "usando apenas as informações obtidas pelas ferramentas. Não chame mais ferramentas.";
    /**
     * Etapa final sem ferramentas: sem este pedido explícito o modelo tenta chamar ferramenta em texto e gera até o
     * limite de tokens com resposta vazia (medido em 16/11/2026: ~15 s perdidos e queda no modo demonstrativo).
     */
    static final String PEDIDO_TEXTO = "Agora escreva a resposta final ao usuário, usando apenas as informações obtidas "
            + "pelas ferramentas. Não chame mais ferramentas.";
    private static final long CACHE_DISPONIBILIDADE_MS = 20_000;
    /** Janela de contexto: a mesma no aquecimento e nas chamadas (outra janela faria o Ollama recarregar o modelo). */
    static final int JANELA_CONTEXTO = 8192;

    private final AgentesConfiguracao configuracao;
    private final RestClient http;
    private final ObjectMapper json;
    private final AtomicLong sequencia = new AtomicLong();
    private volatile long verificadoEm;
    private volatile boolean ultimoEstado;
    private volatile String estadoAquecimento = "NAO_INICIADO";
    private volatile long aquecimentoMs;
    private volatile String aquecidoEm;

    public OllamaClienteModelo(AgentesConfiguracao configuracao, ObjectMapper json) {
        this(configuracao, json, criarHttp(configuracao));
    }

    OllamaClienteModelo(AgentesConfiguracao configuracao, ObjectMapper json, RestClient http) {
        this.configuracao = configuracao;
        this.json = json;
        this.http = http;
    }

    private static RestClient criarHttp(AgentesConfiguracao c) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(java.time.Duration.ofSeconds(3));
        f.setReadTimeout(c.timeout());
        return RestClient.builder().baseUrl(c.ollamaUrl()).requestFactory(f).build();
    }

    /** O Ollama responde e o modelo configurado já foi baixado (resultado guardado por 20 s). */
    @Override
    public boolean disponivel() {
        long agora = System.currentTimeMillis();
        if (agora - verificadoEm < CACHE_DISPONIBILIDADE_MS) {
            return ultimoEstado;
        }
        boolean ok;
        try {
            JsonNode tags = http.get().uri("/api/tags").retrieve().body(JsonNode.class);
            ok = false;
            if (tags != null) {
                for (JsonNode m : tags.path("models")) {
                    String nome = m.path("name").asText();
                    if (nome.equals(configuracao.modeloLocal()) || nome.equals(configuracao.modeloLocal() + ":latest")) {
                        ok = true;
                    }
                }
            }
        } catch (RuntimeException e) {
            ok = false;
        }
        ultimoEstado = ok;
        verificadoEm = agora;
        return ok;
    }

    /** Versão do Ollama, modelos instalados e modelos carregados (memória de vídeo). Falhas viram "erro". */
    @Override
    public Map<String, Object> diagnostico() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("provedor", "Ollama (local)");
        d.put("modeloConfigurado", configuracao.modeloLocal());
        d.put("manterCarregado", configuracao.manterCarregado());
        Map<String, Object> aquecimento = new LinkedHashMap<>();
        aquecimento.put("estado", estadoAquecimento);
        aquecimento.put("duracaoMs", aquecimentoMs);
        aquecimento.put("em", aquecidoEm);
        d.put("aquecimento", aquecimento);
        try {
            long t0 = System.currentTimeMillis();
            JsonNode versao = http.get().uri("/api/version").retrieve().body(JsonNode.class);
            d.put("latenciaMs", System.currentTimeMillis() - t0);
            d.put("versao", versao == null ? null : versao.path("version").asText(null));
            List<Map<String, Object>> instalados = new ArrayList<>();
            JsonNode tags = http.get().uri("/api/tags").retrieve().body(JsonNode.class);
            if (tags != null) {
                for (JsonNode m : tags.path("models")) {
                    instalados.add(Map.of("nome", m.path("name").asText(), "tamanhoMb", m.path("size").asLong() / 1_000_000,
                            "parametros", m.path("details").path("parameter_size").asText(""),
                            "quantizacao", m.path("details").path("quantization_level").asText("")));
                }
            }
            d.put("instalados", instalados);
            List<Map<String, Object>> carregados = new ArrayList<>();
            JsonNode ps = http.get().uri("/api/ps").retrieve().body(JsonNode.class);
            if (ps != null) {
                for (JsonNode m : ps.path("models")) {
                    long total = m.path("size").asLong();
                    long vram = m.path("size_vram").asLong();
                    carregados.add(Map.of("nome", m.path("name").asText(), "memoriaMb", total / 1_000_000,
                            "memoriaVideoMb", vram / 1_000_000,
                            "percentualGpu", total > 0 ? Math.round(vram * 100.0 / total) : 0,
                            "ateQuando", m.path("expires_at").asText("")));
                }
            }
            d.put("carregados", carregados);
        } catch (RuntimeException e) {
            d.put("erro", "Ollama não respondeu (" + e.getClass().getSimpleName() + ")");
        }
        return d;
    }

    /** Carrega o modelo na GPU ({@code /api/generate} sem prompt) e o mantém pelo tempo configurado. */
    @Override
    public boolean aquecer() {
        if (!disponivel()) {
            estadoAquecimento = "INDISPONIVEL";
            return false;
        }
        estadoAquecimento = "AQUECENDO";
        long t0 = System.currentTimeMillis();
        try {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("model", configuracao.modeloLocal());
            corpo.put("keep_alive", configuracao.manterCarregado());
            corpo.put("options", Map.of("num_ctx", JANELA_CONTEXTO));
            http.post().uri("/api/generate").contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve()
                    .body(JsonNode.class);
            aquecimentoMs = System.currentTimeMillis() - t0;
            aquecidoEm = java.time.Instant.now().toString();
            estadoAquecimento = "PRONTO";
            return true;
        } catch (RuntimeException e) {
            aquecimentoMs = System.currentTimeMillis() - t0;
            estadoAquecimento = "FALHOU";
            return false;
        }
    }

    @Override
    public String modelo(String modeloSugerido) {
        return configuracao.modeloLocal();
    }

    @Override
    public RespostaModelo criar(PedidoModelo pedido) {
        List<Map<String, Object>> mensagens = mensagens(pedido);
        Map<String, Object> corpo = corpo(pedido, mensagens);
        if (pedido.ferramentas().isEmpty()) {
            // Etapa sem ferramentas: resposta final direto (texto livre ou esquema JSON), com pedido explícito para
            // não chamar mais ferramentas.
            mensagens.add(Map.of("role", "user", "content", pedido.aceitaTexto() ? PEDIDO_TEXTO : PEDIDO_JSON));
            if (!pedido.aceitaTexto()) {
                corpo.put("format", pedido.esquemaResposta());
            }
            JsonNode r = chat(corpo);
            return new RespostaModelo("stop", List.of(), semRaciocinio(r.path("message").path("content").asText("")),
                    null, r.path("prompt_eval_count").asLong(), r.path("eval_count").asLong(), 0);
        }
        corpo.put("tools", pedido.ferramentas().stream().map(OllamaClienteModelo::definicao).toList());
        JsonNode r = chat(corpo);
        JsonNode mensagem = r.path("message");
        long entrada = r.path("prompt_eval_count").asLong();
        long saida = r.path("eval_count").asLong();

        List<ChamadaFerramenta> chamadas = new ArrayList<>();
        for (JsonNode c : mensagem.path("tool_calls")) {
            JsonNode fn = c.path("function");
            JsonNode argumentos = fn.path("arguments");
            if (argumentos.isTextual()) {
                argumentos = lerOuVazio(argumentos.asText());
            }
            String id = c.hasNonNull("id") ? c.get("id").asText() : "chamada-" + sequencia.incrementAndGet();
            chamadas.add(new ChamadaFerramenta(id, fn.path("name").asText(), argumentos));
        }
        String texto = semRaciocinio(mensagem.path("content").asText(""));
        if (!chamadas.isEmpty()) {
            return new RespostaModelo("tool_calls", chamadas, texto, null, entrada, saida, 0);
        }
        if (respostaCompleta(texto, pedido.esquemaResposta()) || (pedido.aceitaTexto() && !texto.isBlank())) {
            return new RespostaModelo("stop", List.of(), texto, null, entrada, saida, 0);
        }
        // Resposta final estruturada: mesma conversa, sem ferramentas, com o esquema JSON obrigatório.
        mensagens.add(Map.of("role", "assistant", "content", texto));
        mensagens.add(Map.of("role", "user", "content", PEDIDO_JSON));
        Map<String, Object> final_ = corpo(pedido, mensagens);
        final_.put("format", pedido.esquemaResposta());
        JsonNode r2 = chat(final_);
        return new RespostaModelo("stop", List.of(), semRaciocinio(r2.path("message").path("content").asText("")), null,
                entrada + r2.path("prompt_eval_count").asLong(), saida + r2.path("eval_count").asLong(), 0);
    }

    private Map<String, Object> corpo(PedidoModelo pedido, List<Map<String, Object>> mensagens) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("model", configuracao.modeloLocal());
        corpo.put("messages", mensagens);
        corpo.put("stream", false);
        if (configuracao.pensar()) {
            // Modelos com modo de raciocínio: raciocínio separado no campo "thinking" (mais lento).
            corpo.put("think", true);
        }
        corpo.put("keep_alive", configuracao.manterCarregado());
        corpo.put("options", Map.of("temperature", 0.2, "num_ctx", JANELA_CONTEXTO, "num_predict", pedido.maxTokens()));
        return corpo;
    }

    private JsonNode chat(Map<String, Object> corpo) {
        JsonNode r = http.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON).body(corpo)
                .retrieve().body(JsonNode.class);
        if (r == null || r.path("message").isMissingNode()) {
            throw new AgenteIndisponivelException("Resposta vazia do modelo local.");
        }
        return r;
    }

    private List<Map<String, Object>> mensagens(PedidoModelo pedido) {
        List<Map<String, Object>> r = new ArrayList<>();
        r.add(Map.of("role", "system", "content", pedido.instrucoes()));
        for (Mensagem m : pedido.mensagens()) {
            switch (m.papel()) {
                case "assistant" -> {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("role", "assistant");
                    a.put("content", m.texto() == null ? "" : m.texto());
                    if (!m.chamadas().isEmpty()) {
                        a.put("tool_calls", m.chamadas().stream().map(c -> Map.of("function",
                                Map.of("name", c.nome(), "arguments", c.entrada() == null ? Map.of() : c.entrada())))
                                .toList());
                    }
                    r.add(a);
                }
                case "tool" -> r.add(Map.of("role", "tool", "tool_name", m.nomeFerramenta(), "content", m.texto()));
                default -> r.add(Map.of("role", "user", "content", m.texto()));
            }
        }
        return r;
    }

    static Map<String, Object> definicao(FerramentaAgente f) {
        Map<String, Object> parametros = new LinkedHashMap<>();
        parametros.put("type", "object");
        parametros.put("properties", f.propriedades());
        parametros.put("required", f.obrigatorias());
        return Map.of("type", "function", "function",
                Map.of("name", f.nome(), "description", f.descricao(), "parameters", parametros));
    }

    /** Remove um eventual bloco de raciocínio (<think>…</think>) que o modelo tenha deixado no texto. */
    static String semRaciocinio(String texto) {
        int fim = texto.lastIndexOf("</think>");
        return (fim >= 0 ? texto.substring(fim + "</think>".length()) : texto).strip();
    }

    /** JSON objeto com todos os campos obrigatórios do esquema (senão a chamada final impõe o esquema). */
    private boolean respostaCompleta(String texto, Map<String, Object> esquema) {
        try {
            if (texto == null || texto.isBlank()) {
                return false;
            }
            JsonNode n = json.readTree(texto.strip());
            if (n == null || !n.isObject()) {
                return false;
            }
            if (esquema.get("required") instanceof List<?> obrigatorios) {
                for (Object campo : obrigatorios) {
                    if (!n.has(String.valueOf(campo))) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode lerOuVazio(String texto) {
        try {
            return json.readTree(texto);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }
}
