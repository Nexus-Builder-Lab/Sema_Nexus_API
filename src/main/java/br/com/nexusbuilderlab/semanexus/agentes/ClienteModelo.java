package br.com.nexusbuilderlab.semanexus.agentes;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Provedor do modelo de linguagem, independente de fornecedor: modelo local (Ollama, padrão, sem custo) ou Claude
 * (Anthropic, opcional). Permite testar o executor sem rede.
 */
public interface ClienteModelo {

    /** true quando o provedor está configurado e acessível (sem custo de tokens). */
    boolean disponivel();

    /** Nome exibido do modelo efetivamente usado para o agente (ex.: "qwen3:4b-instruct"). */
    String modelo(String modeloSugerido);

    /** Informações do provedor para o Painel do sistema (versão, modelos carregados...). Nunca segredos. */
    default Map<String, Object> diagnostico() {
        return Map.of();
    }

    /**
     * Carrega o modelo na memória (GPU) antes do primeiro uso, para a primeira pergunta não esperar o carregamento.
     * Devolve false quando não se aplica ou falhou. Sem custo de tokens.
     */
    default boolean aquecer() {
        return false;
    }

    /** Envia a requisição e devolve a resposta. Lança exceção em qualquer falha de rede/API. */
    RespostaModelo criar(PedidoModelo pedido);

    /** Pedido de ferramenta feito pelo modelo. */
    record ChamadaFerramenta(String id, String nome, JsonNode entrada) {
    }

    /**
     * Mensagem do histórico. {@code papel}: {@code user}, {@code assistant} (com chamadas de ferramenta e, se houver,
     * a mensagem nativa do provedor em {@code nativo}) ou {@code tool} (resultado de uma ferramenta).
     */
    record Mensagem(String papel, String texto, List<ChamadaFerramenta> chamadas, String idFerramenta,
                    String nomeFerramenta, boolean erro, Object nativo) {

        public static Mensagem usuario(String texto) {
            return new Mensagem("user", texto, List.of(), null, null, false, null);
        }

        public static Mensagem assistente(RespostaModelo r) {
            return new Mensagem("assistant", r.texto(), r.chamadas(), null, null, false, r.nativo());
        }

        public static Mensagem ferramenta(ChamadaFerramenta c, String resultado, boolean erro) {
            return new Mensagem("tool", resultado, List.of(), c.id(), c.nome(), erro, null);
        }
    }

    /**
     * Pedido ao modelo: prompt de sistema, ferramentas disponíveis, histórico, esquema JSON da resposta final,
     * esforço ({@code low}/{@code medium}, quando o provedor suporta) e limite de tokens de saída.
     * {@code aceitaTexto}: o agente aceita a resposta final em texto livre (o provedor local não precisa reescrevê-la
     * no esquema JSON — uma geração a menos).
     */
    record PedidoModelo(String modelo, String instrucoes, List<FerramentaAgente> ferramentas, List<Mensagem> mensagens,
                        Map<String, Object> esquemaResposta, String esforco, long maxTokens, boolean aceitaTexto) {

        public PedidoModelo(String modelo, String instrucoes, List<FerramentaAgente> ferramentas, List<Mensagem> mensagens,
                            Map<String, Object> esquemaResposta, String esforco, long maxTokens) {
            this(modelo, instrucoes, ferramentas, mensagens, esquemaResposta, esforco, maxTokens, false);
        }
    }

    /**
     * Resposta: {@code chamadas} não vazia = o modelo quer ferramentas; vazia = resposta final em {@code texto} (JSON).
     * {@code motivoParada} é o motivo informado pelo provedor (para diagnóstico).
     */
    record RespostaModelo(String motivoParada, List<ChamadaFerramenta> chamadas, String texto, Object nativo,
                          long tokensEntrada, long tokensSaida, long tokensCache) {
    }
}
