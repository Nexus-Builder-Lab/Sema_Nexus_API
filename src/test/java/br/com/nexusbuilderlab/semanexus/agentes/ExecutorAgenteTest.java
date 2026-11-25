package br.com.nexusbuilderlab.semanexus.agentes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo.ChamadaFerramenta;
import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo.PedidoModelo;
import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo.RespostaModelo;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class ExecutorAgenteTest {

    static final String CHAVE_FICTICIA = "sk-ant-" + "x".repeat(30);
    private final ObjectMapper json = new ObjectMapper();
    private final AuditService auditoria = mock(AuditService.class);

    /** Cliente simulado: devolve as respostas em ordem e guarda os pedidos. */
    static class ClienteFalso implements ClienteModelo {
        final Deque<RespostaModelo> respostas = new ArrayDeque<>();
        final List<PedidoModelo> pedidos = new ArrayList<>();
        boolean disponivel = true;

        @Override
        public boolean disponivel() {
            return disponivel;
        }

        @Override
        public String modelo(String modeloSugerido) {
            return "modelo-local";
        }

        @Override
        public RespostaModelo criar(PedidoModelo pedido) {
            pedidos.add(pedido);
            if (respostas.isEmpty()) {
                throw new IllegalStateException("sem resposta simulada");
            }
            return respostas.poll();
        }
    }

    static AgentesConfiguracao config(String provedor, String chave, int maxIteracoes) {
        return new AgentesConfiguracao(provedor, chave, "claude-haiku-5-5", "claude-sonnet-5-5",
                "http://ollama:11434", "qwen3:4b", false, 30, maxIteracoes);
    }

    private static RespostaModelo ferramenta(String id, String nome, String entradaJson) throws Exception {
        return new RespostaModelo("tool_calls",
                List.of(new ChamadaFerramenta(id, nome, new ObjectMapper().readTree(entradaJson))), "", null, 100, 20, 0);
    }

    private static RespostaModelo fim(String texto) {
        return new RespostaModelo("stop", List.of(), texto, null, 150, 40, 90);
    }

    private static DefinicaoAgente agente(List<String> chamadas) {
        FerramentaAgente eco = new FerramentaAgente("eco", "Devolve o texto", Map.of("texto", Map.of("type", "string")),
                List.of("texto"), entrada -> {
                    chamadas.add(entrada.get("texto").asText());
                    return new FerramentaAgente.Resultado(Map.of("eco", entrada.get("texto").asText()), "eco ok");
                });
        FerramentaAgente falha = new FerramentaAgente("falha", "Sempre falha", Map.of(), List.of(), entrada -> {
            throw new IllegalArgumentException("demanda não encontrada");
        });
        return new DefinicaoAgente("teste", "claude-haiku-5-5", "Instruções.", List.of(eco, falha),
                Map.of("type", "object", "properties", Map.of("ok", Map.of("type", "boolean")),
                        "required", List.of("ok"), "additionalProperties", false), "low", 2000, 5);
    }

    private ExecutorAgente executor(ClienteFalso cliente, int maxIteracoes) {
        return new ExecutorAgente(cliente, config("ollama", "", maxIteracoes), auditoria, json);
    }

    @Test
    void executaFerramentasEDevolveRespostaFinal() throws Exception {
        ClienteFalso cliente = new ClienteFalso();
        cliente.respostas.add(ferramenta("t1", "eco", "{\"texto\":\"oi\"}"));
        cliente.respostas.add(ferramenta("t2", "falha", "{}"));
        cliente.respostas.add(fim("```json\n{\"ok\":true}\n```"));
        List<String> chamadas = new ArrayList<>();

        ResultadoAgente r = executor(cliente, 6).executar(agente(chamadas), "Tarefa", "10");

        assertThat(r.resposta().get("ok").asBoolean()).isTrue();
        assertThat(r.modelo()).isEqualTo("modelo-local");
        assertThat(chamadas).containsExactly("oi");
        assertThat(r.passos()).extracting(ResultadoAgente.PassoAgente::ferramenta).containsExactly("eco", "falha");
        // Catálogo de erros públicos: código e mensagem pública, nunca o e.getMessage() da ferramenta.
        assertThat(r.passos().get(1).resumo()).contains("PARAMETRO_INVALIDO").contains("ref. ERR-")
                .doesNotContain("demanda não encontrada");
        assertThat(r.tokensEntrada()).isEqualTo(350);
        // tarefa + (assistente, resultado) x 2 = 5 mensagens no último pedido; erro da ferramenta marcado
        assertThat(cliente.pedidos).hasSize(3);
        assertThat(cliente.pedidos.get(2).mensagens()).hasSize(5);
        assertThat(cliente.pedidos.get(2).mensagens().get(4).erro()).isTrue();
        verify(auditoria).registrar(eq("AGENTE_EXECUCAO"), eq("AGENTE"), eq("teste:10"), contains("eco, falha"));
    }

    @Test
    void depoisDasRodadasDeFerramentasOPedidoVaiSemFerramentas() throws Exception {
        ClienteFalso cliente = new ClienteFalso();
        cliente.respostas.add(ferramenta("t1", "eco", "{\"texto\":\"oi\"}"));
        cliente.respostas.add(fim("Resposta: {\"ok\":true} fim"));
        DefinicaoAgente base = agente(new ArrayList<>());
        DefinicaoAgente umaRodada = new DefinicaoAgente(base.nome(), base.modelo(), base.instrucoes(),
                base.ferramentas(), base.esquemaResposta(), base.esforco(), base.maxTokens(), 1);

        ResultadoAgente r = executor(cliente, 6).executar(umaRodada, "Tarefa", "1");

        assertThat(cliente.pedidos.get(0).ferramentas()).hasSize(2);
        assertThat(cliente.pedidos.get(1).ferramentas()).isEmpty();
        assertThat(r.resposta().get("ok").asBoolean()).isTrue(); // JSON extraído do texto em volta
    }

    @Test
    void provedorIndisponivelNaoChamaOModelo() {
        ClienteFalso cliente = new ClienteFalso();
        cliente.disponivel = false;

        assertThatThrownBy(() -> executor(cliente, 6).executar(agente(new ArrayList<>()), "Tarefa", "1"))
                .isInstanceOf(AgenteIndisponivelException.class).hasMessageContaining("indisponível");
        assertThat(cliente.pedidos).isEmpty();
        verify(auditoria).registrar(eq("AGENTE_FALHA"), eq("AGENTE"), eq("teste:1"), anyString());
    }

    @Test
    void limiteDeIteracoesViraIndisponivel() throws Exception {
        ClienteFalso cliente = new ClienteFalso();
        cliente.respostas.add(ferramenta("t1", "eco", "{\"texto\":\"a\"}"));
        cliente.respostas.add(ferramenta("t2", "eco", "{\"texto\":\"b\"}"));

        assertThatThrownBy(() -> executor(cliente, 2).executar(agente(new ArrayList<>()), "Tarefa", "1"))
                .isInstanceOf(AgenteIndisponivelException.class).hasMessageContaining("Limite de 2");
    }

    @Test
    void respostaInvalidaEErroDeRedeViramIndisponivel() {
        ClienteFalso invalida = new ClienteFalso();
        invalida.respostas.add(fim("isto não é JSON"));
        assertThatThrownBy(() -> executor(invalida, 6).executar(agente(new ArrayList<>()), "T", "1"))
                .isInstanceOf(AgenteIndisponivelException.class).hasMessageContaining("JSON");

        ClienteFalso rede = new ClienteFalso(); // sem respostas: lança IllegalStateException
        assertThatThrownBy(() -> executor(rede, 6).executar(agente(new ArrayList<>()), "T", "1"))
                .isInstanceOf(AgenteIndisponivelException.class).hasMessageContaining("IllegalStateException");
    }

    @Test
    void configuracaoValidaValoresENaoExpoeAChave() {
        AgentesConfiguracao c = config("anthropic", CHAVE_FICTICIA, 6);
        assertThat(c.provedor()).isEqualTo(AgentesConfiguracao.Provedor.ANTHROPIC);
        assertThat(c.chaveAnthropicValida()).isTrue();
        assertThat(c.toString()).doesNotContain(CHAVE_FICTICIA).doesNotContain("sk-ant");
        AgentesConfiguracao invalida = new AgentesConfiguracao("qualquer", "chave", "modelo inválido!", "",
                "ftp://x", "Modelo Ruim!", false, 30, 99);
        assertThat(invalida.provedor()).isEqualTo(AgentesConfiguracao.Provedor.OLLAMA);
        assertThat(invalida.chaveAnthropicValida()).isFalse();
        assertThat(invalida.modeloTriagem()).isEqualTo(AgentesConfiguracao.MODELO_TRIAGEM_PADRAO);
        assertThat(invalida.modeloLocal()).isEqualTo(AgentesConfiguracao.MODELO_LOCAL_PADRAO);
        assertThat(invalida.ollamaUrl()).isEqualTo("http://ollama:11434");
        assertThat(invalida.maxIteracoes()).isEqualTo(10);
    }

    @Test
    void statusInformaModoEModelos() {
        ClienteFalso cliente = new ClienteFalso();
        AgentesConfiguracao local = config("ollama", "", 6);
        var status = new AgentesController(local, new ExecutorAgente(cliente, local, auditoria, json)).status();
        assertThat(status.modo()).isEqualTo(AgentesController.MODO_LOCAL);
        assertThat(status.agentes()).extracting(AgentesController.AgenteInfo::modelo).containsOnly("modelo-local");

        cliente.disponivel = false;
        var demo = new AgentesController(local, new ExecutorAgente(cliente, local, auditoria, json)).status();
        assertThat(demo.modo()).isEqualTo(AgentesController.MODO_DEMONSTRATIVO);
        assertThat(demo.agentes()).extracting(AgentesController.AgenteInfo::modelo)
                .containsOnly(AgentesController.SEM_IA);

        AgentesConfiguracao claude = config("anthropic", CHAVE_FICTICIA, 6);
        AnthropicClienteModelo anthropic = new AnthropicClienteModelo(claude);
        var c = new AgentesController(claude, new ExecutorAgente(anthropic, claude, auditoria, json)).status();
        assertThat(c.modo()).isEqualTo(AgentesController.MODO_CLAUDE);
        assertThat(c.agentes()).extracting(AgentesController.AgenteInfo::modelo)
                .startsWith("claude-haiku-5-5", "claude-sonnet-5-5");
        assertThat(c.agentes()).filteredOn(a -> a.id().equals("minuta"))
                .extracting(AgentesController.AgenteInfo::modelo).containsExactly("claude-sonnet-5-5");
        assertThat(c.agentes()).filteredOn(a -> a.id().equals("sentinela"))
                .extracting(AgentesController.AgenteInfo::modelo).containsExactly("claude-haiku-5-5");
        assertThat(c.toString()).doesNotContain(CHAVE_FICTICIA);
    }

    /** Observador que guarda os eventos (Issue #135). */
    static class ObservadorGravador implements ObservadorAgente {
        final List<EventoAgente> eventos = new ArrayList<>();
        boolean cancelar;

        @Override
        public void emitir(EventoAgente evento) {
            eventos.add(evento);
        }

        @Override
        public boolean cancelado() {
            return cancelar;
        }
    }

    @Test
    void emiteEventosDeFerramentaEmOrdemComErroSanitizado() throws Exception {
        ClienteFalso cliente = new ClienteFalso();
        cliente.respostas.add(ferramenta("t1", "eco", "{\"texto\":\"oi\"}"));
        cliente.respostas.add(ferramenta("t2", "falha", "{}"));
        cliente.respostas.add(ferramenta("t3", "rm -rf/;", "{}"));
        cliente.respostas.add(fim("{\"ok\":true}"));
        ObservadorGravador obs = new ObservadorGravador();

        ResultadoAgente r = executor(cliente, 6).executar(agente(new ArrayList<>()), "Tarefa", "11", obs);

        assertThat(r.resposta().get("ok").asBoolean()).isTrue();
        assertThat(obs.eventos).extracting(EventoAgente::tipo).containsExactly(
                "ferramenta_iniciada", "ferramenta_concluida", "ferramenta_iniciada", "ferramenta_concluida",
                "ferramenta_iniciada", "ferramenta_concluida");
        assertThat(obs.eventos.get(0).dados()).containsEntry("ferramenta", "eco").containsEntry("ordem", 1);
        assertThat(obs.eventos.get(1).dados()).containsEntry("resumo", "eco ok").containsEntry("erro", false);
        // Erro da ferramenta: só o catálogo público + referência, nunca a mensagem interna.
        assertThat(obs.eventos.get(3).dados()).containsEntry("erro", true);
        assertThat((String) obs.eventos.get(3).dados().get("resumo")).contains("PARAMETRO_INVALIDO")
                .contains("ref. ERR-").doesNotContain("demanda não encontrada");
        // Nome vindo do modelo ecoado só com caracteres seguros.
        assertThat(obs.eventos.get(4).dados()).containsEntry("ferramenta", "rmrf");
        assertThat(obs.eventos.get(5).dados()).containsEntry("erro", true);
        verify(auditoria).registrar(eq("AGENTE_EXECUCAO"), eq("AGENTE"), eq("teste:11"), anyString());
    }

    @Test
    void observadorComFalhaNaoInterrompeOAgente() throws Exception {
        ClienteFalso cliente = new ClienteFalso();
        cliente.respostas.add(ferramenta("t1", "eco", "{\"texto\":\"oi\"}"));
        cliente.respostas.add(fim("{\"ok\":true}"));
        ObservadorAgente quebrado = evento -> {
            throw new IllegalStateException("cliente desconectado");
        };

        assertThat(executor(cliente, 6).executar(agente(new ArrayList<>()), "Tarefa", "12", quebrado)
                .resposta().get("ok").asBoolean()).isTrue();
    }

    @Test
    void cancelamentoParaAntesDaProximaChamadaEAudita() throws Exception {
        ClienteFalso cliente = new ClienteFalso();
        cliente.respostas.add(ferramenta("t1", "eco", "{\"texto\":\"oi\"}"));
        cliente.respostas.add(fim("{\"ok\":true}"));
        List<String> chamadas = new ArrayList<>();
        ObservadorGravador obs = new ObservadorGravador() {
            @Override
            public void emitir(EventoAgente evento) {
                super.emitir(evento);
                if (EventoAgente.FERRAMENTA_CONCLUIDA.equals(evento.tipo())) {
                    cancelar = true; // o cliente desconecta logo após a primeira ferramenta
                }
            }
        };

        assertThatThrownBy(() -> executor(cliente, 6).executar(agente(chamadas), "Tarefa", "13", obs))
                .isInstanceOf(ExecucaoCanceladaException.class)
                .isInstanceOf(AgenteIndisponivelException.class);
        assertThat(cliente.pedidos).hasSize(1); // a segunda chamada ao modelo não aconteceu
        assertThat(chamadas).containsExactly("oi");
        verify(auditoria).registrar(eq("AGENTE_FALHA"), eq("AGENTE"), eq("teste:13"), contains("cancelada"));
    }

    @Test
    void semObservadorContinuaIgual() throws Exception {
        ClienteFalso cliente = new ClienteFalso();
        cliente.respostas.add(fim("{\"ok\":true}"));
        assertThat(executor(cliente, 6).executar(agente(new ArrayList<>()), "Tarefa", "14", null)
                .resposta().get("ok").asBoolean()).isTrue();
    }
}
