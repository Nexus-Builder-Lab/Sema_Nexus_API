package br.com.nexusbuilderlab.semanexus.common.exception;

/** Login recusado. A mensagem é sempre genérica: não revela se o e-mail existe. → HTTP 401. */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha inválidos.");
    }
}
