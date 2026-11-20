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
import org.springframework.beans.factory.annotation.Autowired;
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
    private final MetricasAgentes metricas;

    @Autowired
    public ExecutorAgente(ClienteModelo cliente, AgentesConfiguracao configuracao, AuditService auditoria,
                          ObjectMapper json, MetricasAgentes metricas) {
        this.cliente = cliente;
        this.configuracao = configuracao;
        this.auditoria = auditoria;
        this.json = json;
        this.metricas = metricas;
    }

    /** Sem métricas compartilhadas (testes). */
    public ExecutorAgente(ClienteModelo cliente, AgentesConfiguracao configuracao, AuditService auditoria,
                          ObjectMapper json) {
        this(cliente, configuracao, auditoria, json, new MetricasAgentes());
    }

    /** Informações do provedor para o Painel do sistema (sem segredos). */
    public Map<String, Object> diagnosticoProvedor() {
        return cliente.diagnostico();
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
        long inicio = System.currentTimeMillis();
        long prazo = inicio + configuracao.orcamento().toMillis();
        int[] chamadasFerramenta = {0};
        boolean tentou = false;
        try {
            if (!cliente.disponivel()) {
                throw new AgenteIndisponivelException("Provedor de IA (" + configuracao.provedor()
                        + ") indisponível ou modelo não baixado.");
            }
            tentou = true;
            Map<String, FerramentaAgente> ferramentas = new LinkedHashMap<>();
            agente.ferramentas().forEach(f -> ferramentas.put(f.nome(), f));
            List<Mensagem> mensagens = new ArrayList<>();
            mensagens.add(Mensagem.usuario(tarefa));
            int rodadas = 0;
            for (int iteracao = 0; iteracao < configuracao.maxIteracoes(); iteracao++) {
                verificarOrcamento(prazo);
                // Esgotadas as rodadas de ferramentas, o pedido vai sem ferramentas: resposta final no esquema.
                List<FerramentaAgente> disponiveis = rodadas < agente.maxRodadasFerramentas()
                        ? agente.ferramentas() : List.of();
                RespostaModelo resposta = chamar(new PedidoModelo(modelo, agente.instrucoes(), disponiveis,
                        List.copyOf(mensagens), agente.esquemaResposta(), agente.esforco(), agente.maxTokens(),
                        agente.campoTexto() != null));
                entrada += resposta.tokensEntrada();
                saida += resposta.tokensSaida();
                cache += resposta.tokensCache();
                if (!resposta.chamadas().isEmpty()) {
                    rodadas++;
                    mensagens.add(Mensagem.assistente(resposta));
                    for (ChamadaFerramenta chamada : resposta.chamadas()) {
                        if (++chamadasFerramenta[0] > configuracao.maxChamadasFerramenta()) {
                            throw new AgenteIndisponivelException("Orçamento de "
                                    + configuracao.maxChamadasFerramenta() + " chamadas de ferramenta esgotado.");
                        }
                        mensagens.add(executarFerramenta(ferramentas, chamada, passos));
                    }
                    continue;
                }
                ResultadoAgente r = new ResultadoAgente(lerJson(resposta.texto(), agente.campoTexto()),
                        List.copyOf(passos), modelo,
                        entrada, saida, cache);
                long duracao = System.currentTimeMillis() - inicio;
                auditar(agente, modelo, entidadeId, "AGENTE_EXECUCAO", passos, entrada, saida, cache, duracao,
                        null);
                metricas.sucesso(agente.nome(), modelo, duracao);
                LOG.info("Agente {} concluiu: provedor={}, modelo={}, ferramentas={}, tokens entrada={} saída={}, "
                        + "latência={} ms", agente.nome(), configuracao.provedor(), modelo, passos.size(), entrada,
                        saida, duracao);
                return r;
            }
            throw new AgenteIndisponivelException("Limite de " + configuracao.maxIteracoes()
                    + " iterações atingido sem resposta final.");
        } catch (AgenteIndisponivelException e) {
            auditar(agente, modelo, entidadeId, "AGENTE_FALHA", passos, entrada, saida, cache,
                    System.currentTimeMillis() - inicio, e.getMessage());
            if (tentou) {
                metricas.falha(agente.nome(), modelo, System.currentTimeMillis() - inicio, e.getMessage());
            }
            LOG.warn("Agente {} indisponível; usando o modo demonstrativo ({})", agente.nome(), e.getMessage());
            throw e;
        }
    }

    /** Registra na auditoria uma execução feita pelas regras locais do modo demonstrativo. */
    public void registrarDemonstrativo(String agente, String entidadeId, String detalhe) {
        metricas.demonstrativo(agente);
        auditoria.registrar("AGENTE_DEMONSTRATIVO", "AGENTE", agente + ":" + entidadeId,
                "Modo demonstrativo (regras locais, sem IA generativa). " + detalhe);
    }

    /** Conta um uso do modo demonstrativo sem auditoria própria (ex.: DocIA extrativo, que já identifica o modo). */
    public void contarDemonstrativo(String agente) {
        metricas.demonstrativo(agente);
    }

    /**
     * Interrompe a execução quando o orçamento total de tempo acabou. Uma chamada já em andamento ainda respeita o
     * timeout por chamada, então o pior caso é orçamento + timeout.
     */
    private void verificarOrcamento(long prazo) {
        if (System.currentTimeMillis() > prazo) {
            throw new AgenteIndisponivelException("Orçamento de " + configuracao.orcamento().toSeconds()
                    + " s da execução esgotado.");
        }
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
            // O nome vem do modelo: ecoado só com caracteres seguros, e a ferramenta não é executada.
            String nome = ErroFerramenta.nomeSeguro(chamada.nome());
            passos.add(new PassoAgente(nome, "Ferramenta inexistente (ignorada)."));
            return Mensagem.ferramenta(chamada, ErroFerramenta.FERRAMENTA_INEXISTENTE.name() + ": "
                    + ErroFerramenta.FERRAMENTA_INEXISTENTE.mensagemPublica(), true);
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
            // Catálogo de erros públicos: o modelo, os passos e a auditoria recebem só código + mensagem pública +
            // referência. O detalhe técnico (e.getMessage) fica só no log, ligado pela mesma referência.
            ErroFerramenta erro = ErroFerramenta.de(e);
            String referencia = ErroFerramenta.novaReferencia();
            LOG.warn("Ferramenta {} falhou [{}] {}: {} - {}", f.nome(), referencia, erro, e.getClass().getName(),
                    e.getMessage());
            passos.add(new PassoAgente(f.nome(), "Erro " + erro.publico(referencia)));
            return Mensagem.ferramenta(chamada, erro.publico(referencia), true);
        }
    }

    private JsonNode lerJson(String texto, String campoTexto) {
        String t = texto == null ? "" : texto.strip();
        if (campoTexto != null && !t.isEmpty() && !t.startsWith("{") && !t.startsWith("`")) {
            // Agente que aceita texto livre: a resposta em prosa vai para o campo de texto do JSON.
            return json.createObjectNode().put(campoTexto, t);
        }
        // Modelos locais às vezes embrulham o JSON em bloco de código ou texto: usa do primeiro { ao último }.
        int ini = t.indexOf('{');
        int fim = t.lastIndexOf('}');
        if (ini >= 0 && fim > ini) {
            t = t.substring(ini, fim + 1);
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
                         List<PassoAgente> passos, long entrada, long saida, long cache, long duracaoMs,
                         String falha) {
        String ferramentasUsadas = passos.stream().map(PassoAgente::ferramenta).collect(Collectors.joining(", "));
        auditoria.registrar(acao, "AGENTE", agente.nome() + ":" + entidadeId,
                "Provedor " + configuracao.provedor() + "; modelo " + modelo + "; ferramentas [" + ferramentasUsadas
                        + "]; chamadas de ferramenta " + passos.size() + "; latência " + duracaoMs + " ms"
                        + "; tokens entrada " + entrada + ", saída " + saida + ", cache " + cache
                        + (falha == null ? "" : "; falha: " + falha));
    }
}
