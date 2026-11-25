package br.com.nexusbuilderlab.semanexus.agentes;

/**
 * A execução foi interrompida porque o cliente desistiu (ex.: desconectou do stream SSE, Issue #135). Estende
 * {@link AgenteIndisponivelException} para os chamadores atuais seguirem compatíveis; quem transmite deve checar
 * {@link ObservadorAgente#cancelado()} antes de cair no modo demonstrativo.
 */
public class ExecucaoCanceladaException extends AgenteIndisponivelException {

    public ExecucaoCanceladaException() {
        super("Execução cancelada: o cliente desconectou.");
    }
}
