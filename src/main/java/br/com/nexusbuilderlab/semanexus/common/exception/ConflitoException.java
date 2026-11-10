package br.com.nexusbuilderlab.semanexus.common.exception;

/**
 * Operação incompatível com o estado atual do recurso (ex.: transição de status inválida,
 * vínculo duplicado). Resulta em HTTP 409.
 */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
