package br.com.nexusbuilderlab.semanexus.agentes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo.ChamadaFerramenta;
import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo.Mensagem;
import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo.PedidoModelo;
import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo.RespostaModelo;
import br.com.nexusbuilderlab.semanexus.agentes.ResultadoAgente.PassoAgente;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Laço de ferramentas do agente, independente do provedor: envia a tarefa, executa as ferramentas pedidas pelo
 * modelo, devolve os resultados e repete até a resposta final em JSON ou até o limite de iterações. Qualquer falha
 * vira {@link AgenteIndisponivelException} para o chamador usar o modo demonstrativo. Toda execução é auditada com
 * provedor, modelo, ferramentas e tokens — nunca chaves.
 */
@Component
public class ExecutorAgente {

    static final int LIMITE_RESULTADO = 12_000;
    private static final Logger LOG = LoggerFactory.getLogger(ExecutorAgente.class);

    private final ClienteModelo cliente;
    private final AgentesConfiguracao configuracao;
    private final AuditService auditoria;
    private final ObjectMapper json;

    public ExecutorAgente(ClienteModelo cliente, AgentesConfiguracao configuracao, AuditService auditoria,
                          ObjectMapper json) {
        this.cliente = cliente;
        this.configuracao = configuracao;
        this.auditoria = auditoria;
        this.json = json;
    }

    /** true quando o provedor configurado está acessível (com o Ollama: serviço no ar e modelo baixado). */
    public boolean iaDisponivel() {
        return cliente.disponivel();
    }

    /** Modelo efetivamente usado para o agente (o provedor local usa sempre o seu modelo). */
    public String modelo(String modeloSugerido) {
        return cliente.modelo(modeloSugerido);
    }

    /**
     * Executa o agente. {@code entidadeId} identifica o registro de negócio na auditoria (ex.: id da demanda).
     *
     * @throws AgenteIndisponivelException sem provedor, em erro da API, recusa, resposta inválida ou limite de iterações
     */
    public ResultadoAgente executar(DefinicaoAgente agente, String tarefa, String entidadeId) {
        String modelo = cliente.modelo(agente.modelo());
        List<PassoAgente> passos = new ArrayList<>();
        long entrada = 0;
        long saida = 0;
        long cache = 0;
        try {
            if (!cliente.disponivel()) {
                throw new AgenteIndisponivelException("Provedor de IA (" + configuracao.provedor()
                        + ") indisponível ou modelo não baixado.");
            }
            Map<String, FerramentaAgente> ferramentas = new LinkedHashMap<>();
            agente.ferramentas().forEach(f -> ferramentas.put(f.nome(), f));
            List<Mensagem> mensagens = new ArrayList<>();
            mensagens.add(Mensagem.usuario(tarefa));
            for (int iteracao = 0; iteracao < configuracao.maxIteracoes(); iteracao++) {
                RespostaModelo resposta = chamar(new PedidoModelo(modelo, agente.instrucoes(), agente.ferramentas(),
                        List.copyOf(mensagens), agente.esquemaResposta(), agente.esforco(), agente.maxTokens()));
                entrada += resposta.tokensEntrada();
                saida += resposta.tokensSaida();
                cache += resposta.tokensCache();
                if (!resposta.chamadas().isEmpty()) {
                    mensagens.add(Mensagem.assistente(resposta));
                    for (ChamadaFerramenta chamada : resposta.chamadas()) {
                        mensagens.add(executarFerramenta(ferramentas, chamada, passos));
                    }
                    continue;
                }
                ResultadoAgente r = new ResultadoAgente(lerJson(resposta.texto()), List.copyOf(passos), modelo,
                        entrada, saida, cache);
                auditar(agente, modelo, entidadeId, "AGENTE_EXECUCAO", passos, entrada, saida, cache, null);
                LOG.info("Agente {} concluiu: provedor={}, modelo={}, ferramentas={}, tokens entrada={} saída={}",
                        agente.nome(), configuracao.provedor(), modelo, passos.size(), entrada, saida);
                return r;
            }
            throw new AgenteIndisponivelException("Limite de " + configuracao.maxIteracoes()
                    + " iterações atingido sem resposta final.");
        } catch (AgenteIndisponivelException e) {
            auditar(agente, modelo, entidadeId, "AGENTE_FALHA", passos, entrada, saida, cache, e.getMessage());
            LOG.warn("Agente {} indisponível; usando o modo demonstrativo ({})", agente.nome(), e.getMessage());
            throw e;
        }
    }

    /** Registra na auditoria uma execução feita pelas regras locais do modo demonstrativo. */
    public void registrarDemonstrativo(String agente, String entidadeId, String detalhe) {
        auditoria.registrar("AGENTE_DEMONSTRATIVO", "AGENTE", agente + ":" + entidadeId,
                "Modo demonstrativo (regras locais, sem IA generativa). " + detalhe);
    }

    private RespostaModelo chamar(PedidoModelo pedido) {
        try {
            return cliente.criar(pedido);
        } catch (AgenteIndisponivelException e) {
            throw e;
        } catch (RuntimeException e) {
            // Só o tipo do erro: a mensagem de exceções HTTP pode trazer cabeçalhos ou corpo da requisição.
            throw new AgenteIndisponivelException("Falha ao chamar o modelo (" + e.getClass().getSimpleName() + ").",
                    e);
        }
    }

    private Mensagem executarFerramenta(Map<String, FerramentaAgente> ferramentas, ChamadaFerramenta chamada,
                                        List<PassoAgente> passos) {
        FerramentaAgente f = ferramentas.get(chamada.nome());
        if (f == null) {
            passos.add(new PassoAgente(chamada.nome(), "Ferramenta inexistente (ignorada)."));
            return Mensagem.ferramenta(chamada, "Ferramenta inexistente: " + chamada.nome(), true);
        }
        try {
            JsonNode entrada = chamada.entrada() == null ? json.createObjectNode() : chamada.entrada();
            FerramentaAgente.Resultado resultado = f.executar().apply(entrada);
            passos.add(new PassoAgente(f.nome(), resultado.resumo()));
            String texto = json.writeValueAsString(resultado.dados());
            if (texto.length() > LIMITE_RESULTADO) {
                texto = texto.substring(0, LIMITE_RESULTADO) + "…(resultado truncado)";
            }
            return Mensagem.ferramenta(chamada, texto, false);
        } catch (RuntimeException | JsonProcessingException e) {
            // Erro de negócio (ex.: 403/404 do Service) volta ao modelo como erro da ferramenta, sem detalhes internos.
            String motivo = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            passos.add(new PassoAgente(f.nome(), "Erro: " + motivo));
            return Mensagem.ferramenta(chamada, "Erro ao executar a ferramenta: " + motivo, true);
        }
    }

    private JsonNode lerJson(String texto) {
        String t = texto == null ? "" : texto.strip();
        // Alguns modelos locais embrulham o JSON em bloco de código.
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            JsonNode n = json.readTree(t);
            if (n == null || !n.isObject()) {
                throw new AgenteIndisponivelException("Resposta final do modelo não é um objeto JSON.");
            }
            return n;
        } catch (JsonProcessingException e) {
            throw new AgenteIndisponivelException("Resposta final do modelo não é JSON válido.", e);
        }
    }

    private void auditar(DefinicaoAgente agente, String modelo, String entidadeId, String acao,
                         List<PassoAgente> passos, long entrada, long saida, long cache, String falha) {
        String ferramentasUsadas = passos.stream().map(PassoAgente::ferramenta).collect(Collectors.joining(", "));
        auditoria.registrar(acao, "AGENTE", agente.nome() + ":" + entidadeId,
                "Provedor " + configuracao.provedor() + "; modelo " + modelo + "; ferramentas [" + ferramentasUsadas
                        + "]; tokens entrada " + entrada + ", saída " + saida + ", cache " + cache
                        + (falha == null ? "" : "; falha: " + falha));
    }
}
