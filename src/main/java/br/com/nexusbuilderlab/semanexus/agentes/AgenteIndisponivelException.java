package br.com.nexusbuilderlab.semanexus.agentes;

/**
 * O agente não conseguiu concluir (sem chave, erro de rede/API, recusa, limite de iterações, resposta inválida).
 * Quem chama deve cair no modo demonstrativo. A mensagem nunca contém a chave nem dados sensíveis.
 */
public class AgenteIndisponivelException extends RuntimeException {

    public AgenteIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public AgenteIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
