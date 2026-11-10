package br.com.nexusbuilderlab.semanexus.common.exception;

/**
 * Endpoint cujo contrato já está publicado no OpenAPI, mas cuja implementação pertence a outra Issue.
 * Resulta em HTTP 501. Cada implementação real remove o uso desta exceção.
 */
public class ContratoPendenteException extends RuntimeException {

    private final int issue;

    public ContratoPendenteException(int issue) {
        super("Endpoint do contrato publicado, ainda não implementado (Issue #" + issue + ").");
        this.issue = issue;
    }

    public int getIssue() {
        return issue;
    }
}
