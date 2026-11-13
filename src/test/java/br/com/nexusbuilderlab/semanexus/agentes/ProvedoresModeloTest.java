package br.com.nexusbuilderlab.semanexus.agentes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo.ChamadaFerramenta;
import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo.Mensagem;
import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo.PedidoModelo;
import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo.RespostaModelo;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProvedoresModeloTest {

    private final ObjectMapper json = new ObjectMapper();

    private static FerramentaAgente buscar() {
        return new FerramentaAgente("buscar", "Busca", Map.of("q", Map.of("type", "string")), List.of("q"),
                e -> new FerramentaAgente.Resultado(List.of(), "0 resultados"));
    }

    private static PedidoModelo pedido(List<Mensagem> mensagens) {
        return new PedidoModelo("ignorado", "Você é um agente.", List.of(buscar()), mensagens,
                Map.of("type", "object", "properties", Map.of("ok", Map.of("type", "boolean")),
                        "required", List.of("ok")), "low", 800);
    }

    private OllamaClienteModelo ollama(RestClient.Builder builder) {
        AgentesConfiguracao c = ExecutorAgenteTest.config("ollama", "", 6);
        return new OllamaClienteModelo(c, json, builder.baseUrl("http://ollama:11434").build());
    }

    @Test
    void ollamaConverteChamadasDeFerramenta() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("http://ollama:11434/api/chat")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"model\":\"qwen3:4b\""),
                        org.hamcrest.Matchers.containsString("\"tools\""),
                        org.hamcrest.Matchers.containsString("\"think\":true"))))
                .andRespond(withSuccess("{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":"
                        + "[{\"function\":{\"name\":\"buscar\",\"arguments\":{\"q\":\"queimada\"}}}]},"
                        + "\"prompt_eval_count\":120,\"eval_count\":15}", MediaType.APPLICATION_JSON));

        RespostaModelo r = ollama(builder).criar(pedido(List.of(Mensagem.usuario("Pergunta"))));

        assertThat(r.chamadas()).hasSize(1);
        assertThat(r.chamadas().get(0).nome()).isEqualTo("buscar");
        assertThat(r.chamadas().get(0).entrada().get("q").asText()).isEqualTo("queimada");
        assertThat(r.chamadas().get(0).id()).isNotBlank();
        assertThat(r.tokensEntrada()).isEqualTo(120);
        servidor.verify();
    }

    @Test
    void ollamaPedeRespostaEstruturadaQuandoOModeloRespondeEmProsa() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("http://ollama:11434/api/chat"))
                .andRespond(withSuccess("{\"message\":{\"role\":\"assistant\",\"content\":\"Está tudo ok.\"},"
                        + "\"prompt_eval_count\":200,\"eval_count\":10}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://ollama:11434/api/chat"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"format\""),
                        org.hamcrest.Matchers.containsString("somente com o objeto JSON final"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"tools\"")))))
                .andRespond(withSuccess("{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"ok\\\":true}\"},"
                        + "\"prompt_eval_count\":220,\"eval_count\":5}", MediaType.APPLICATION_JSON));
        var chamada = new ChamadaFerramenta("c1", "buscar", json.readTree("{\"q\":\"x\"}"));
        List<Mensagem> historico = List.of(Mensagem.usuario("Pergunta"),
                Mensagem.assistente(new RespostaModelo("tool_calls", List.of(chamada), "", null, 0, 0, 0)),
                Mensagem.ferramenta(chamada, "[]", false));

        RespostaModelo r = ollama(builder).criar(pedido(historico));

        assertThat(r.chamadas()).isEmpty();
        assertThat(json.readTree(r.texto()).get("ok").asBoolean()).isTrue();
        assertThat(r.tokensEntrada()).isEqualTo(420);
        servidor.verify();
    }

    @Test
    void ollamaDisponivelSoComOModeloBaixado() {
        RestClient.Builder b1 = RestClient.builder();
        MockRestServiceServer s1 = MockRestServiceServer.bindTo(b1).build();
        s1.expect(requestTo("http://ollama:11434/api/tags")).andRespond(withSuccess(
                "{\"models\":[{\"name\":\"qwen3:4b\"}]}", MediaType.APPLICATION_JSON));
        assertThat(ollama(b1).disponivel()).isTrue();

        RestClient.Builder b2 = RestClient.builder();
        MockRestServiceServer s2 = MockRestServiceServer.bindTo(b2).build();
        s2.expect(requestTo("http://ollama:11434/api/tags")).andRespond(withSuccess(
                "{\"models\":[{\"name\":\"llama3.2:3b\"}]}", MediaType.APPLICATION_JSON));
        assertThat(ollama(b2).disponivel()).isFalse();

        RestClient.Builder b3 = RestClient.builder();
        MockRestServiceServer s3 = MockRestServiceServer.bindTo(b3).build();
        s3.expect(requestTo("http://ollama:11434/api/tags")).andRespond(withServerError());
        assertThat(ollama(b3).disponivel()).isFalse();
    }

    @Test
    void anthropicMontaRequisicaoComCacheFerramentasEResultadosAgrupados() throws Exception {
        var c1 = new ChamadaFerramenta("toolu_1", "buscar", json.readTree("{\"q\":\"a\"}"));
        var c2 = new ChamadaFerramenta("toolu_2", "buscar", json.readTree("{\"q\":\"b\"}"));
        List<Mensagem> historico = List.of(Mensagem.usuario("Pergunta"),
                Mensagem.assistente(new RespostaModelo("tool_use", List.of(c1, c2), "", MessageParam.ofAssistant("."),
                        0, 0, 0)),
                Mensagem.ferramenta(c1, "[]", false), Mensagem.ferramenta(c2, "erro", true));
        AgentesConfiguracao c = ExecutorAgenteTest.config("anthropic", ExecutorAgenteTest.CHAVE_FICTICIA, 6);

        MessageCreateParams p = AnthropicClienteModelo.parametros(new PedidoModelo("claude-haiku-5-5", "Sistema",
                List.of(buscar()), historico, Map.of("type", "object"), "low", 800));

        assertThat(p.model().asString()).isEqualTo("claude-haiku-5-5");
        // usuário, assistente e UMA mensagem com os dois resultados
        assertThat(p.messages()).hasSize(3);
        assertThat(p.tools()).isPresent();
        String serializado = json.writeValueAsString(p._body());
        assertThat(serializado).contains("\"cache_control\"").contains("toolu_1").contains("toolu_2")
                .contains("\"json_schema\"").doesNotContain(ExecutorAgenteTest.CHAVE_FICTICIA);
        assertThat(new AnthropicClienteModelo(c).disponivel()).isTrue();
    }
}
