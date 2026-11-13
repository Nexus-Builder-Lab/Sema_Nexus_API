package br.com.nexusbuilderlab.semanexus.agentes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Provedor Claude (Anthropic), opcional: exige {@code AGENTES_PROVEDOR=anthropic} e {@code ANTHROPIC_API_KEY} com
 * créditos. Usa o SDK oficial, a API de mensagens não-beta, prompt de sistema com cache e resposta final em JSON com
 * esquema (structured outputs). O cliente HTTP só é criado na primeira chamada.
 */
public class AnthropicClienteModelo implements ClienteModelo {

    private final AgentesConfiguracao configuracao;
    private volatile AnthropicClient cliente;

    public AnthropicClienteModelo(AgentesConfiguracao configuracao) {
        this.configuracao = configuracao;
    }

    @Override
    public boolean disponivel() {
        return configuracao.chaveAnthropicValida();
    }

    /** Só se a chave está configurada (nunca o valor) e os modelos escolhidos. */
    @Override
    public Map<String, Object> diagnostico() {
        return Map.of("provedor", "Claude (Anthropic)", "chaveConfigurada", disponivel(),
                "modeloTriagem", configuracao.modeloTriagem(), "modeloDocia", configuracao.modeloDocia());
    }

    @Override
    public String modelo(String modeloSugerido) {
        return modeloSugerido;
    }

    @Override
    public RespostaModelo criar(PedidoModelo pedido) {
        Message m = cliente().messages().create(parametros(pedido));
        List<ChamadaFerramenta> chamadas = new ArrayList<>();
        StringBuilder texto = new StringBuilder();
        for (ContentBlock bloco : m.content()) {
            bloco.toolUse().ifPresent(t ->
                    chamadas.add(new ChamadaFerramenta(t.id(), t.name(), t._input().convert(JsonNode.class))));
            bloco.text().ifPresent(t -> texto.append(t.text()));
        }
        String motivo = m.stopReason().map(r -> r.asString()).orElse("desconhecido");
        if (!"tool_use".equals(motivo) && !"end_turn".equals(motivo)) {
            throw new AgenteIndisponivelException("O modelo encerrou sem resposta final (motivo: " + motivo + ").");
        }
        return new RespostaModelo(motivo, "tool_use".equals(motivo) ? chamadas : List.of(), texto.toString(),
                m.toParam(), m.usage().inputTokens(), m.usage().outputTokens(),
                m.usage().cacheReadInputTokens().orElse(0L));
    }

    static MessageCreateParams parametros(PedidoModelo pedido) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(pedido.modelo())
                .maxTokens(pedido.maxTokens())
                // Prompt de sistema estável e com cache: as ferramentas (renderizadas antes) entram no mesmo prefixo.
                .systemOfTextBlockParams(List.of(TextBlockParam.builder().text(pedido.instrucoes())
                        .cacheControl(CacheControlEphemeral.builder().build()).build()))
                .messages(mensagens(pedido.mensagens()))
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(pedido.esforco()))
                        .format(JsonOutputFormat.builder().schema(esquema(pedido)).build())
                        .build());
        pedido.ferramentas().forEach(f -> b.addTool(definicao(f)));
        return b.build();
    }

    /** Converte o histórico neutro; resultados de ferramentas consecutivos vão juntos numa só mensagem do usuário. */
    static List<MessageParam> mensagens(List<Mensagem> historico) {
        List<MessageParam> r = new ArrayList<>();
        List<ContentBlockParam> resultados = new ArrayList<>();
        for (Mensagem m : historico) {
            if ("tool".equals(m.papel())) {
                resultados.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(m.idFerramenta()).content(m.texto()).isError(m.erro()).build()));
                continue;
            }
            if (!resultados.isEmpty()) {
                r.add(MessageParam.ofUserOfContentBlockParams(List.copyOf(resultados)));
                resultados.clear();
            }
            if ("assistant".equals(m.papel())) {
                // A mensagem nativa preserva blocos de raciocínio e de ferramenta exatamente como vieram.
                r.add(m.nativo() instanceof MessageParam nativa ? nativa : MessageParam.ofAssistant(m.texto()));
            } else {
                r.add(MessageParam.ofUser(m.texto()));
            }
        }
        if (!resultados.isEmpty()) {
            r.add(MessageParam.ofUserOfContentBlockParams(List.copyOf(resultados)));
        }
        return r;
    }

    static Tool definicao(FerramentaAgente f) {
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        f.propriedades().forEach((nome, esquema) -> props.putAdditionalProperty(nome, JsonValue.from(esquema)));
        return Tool.builder()
                .name(f.nome())
                .description(f.descricao())
                .inputSchema(Tool.InputSchema.builder()
                        .properties(props.build())
                        .required(f.obrigatorias())
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    private static JsonOutputFormat.Schema esquema(PedidoModelo pedido) {
        JsonOutputFormat.Schema.Builder b = JsonOutputFormat.Schema.builder();
        semLimites(pedido.esquemaResposta()).forEach((k, v) -> b.putAdditionalProperty(k, JsonValue.from(v)));
        return b.build();
    }

    /** Limites de tamanho servem aos modelos locais; os structured outputs do Claude não os aceitam. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> semLimites(Map<String, Object> esquema) {
        Map<String, Object> r = new LinkedHashMap<>();
        esquema.forEach((k, v) -> {
            if (k.equals("maxLength") || k.equals("minLength") || k.equals("maxItems") || k.equals("minItems")) {
                return;
            }
            r.put(k, v instanceof Map<?, ?> m ? semLimites((Map<String, Object>) m) : v);
        });
        return r;
    }

    private AnthropicClient cliente() {
        if (!disponivel()) {
            throw new AgenteIndisponivelException("ANTHROPIC_API_KEY ausente ou inválida.");
        }
        AnthropicClient c = cliente;
        if (c == null) {
            synchronized (this) {
                c = cliente;
                if (c == null) {
                    c = AnthropicOkHttpClient.builder()
                            .apiKey(configuracao.chave())
                            .timeout(configuracao.timeout())
                            .maxRetries(1)
                            .build();
                    cliente = c;
                }
            }
        }
        return c;
    }
}
