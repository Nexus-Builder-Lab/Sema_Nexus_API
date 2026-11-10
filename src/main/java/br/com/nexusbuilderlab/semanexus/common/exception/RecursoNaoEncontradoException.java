package br.com.nexusbuilderlab.semanexus.common.exception;

/** Recurso inexistente (ou excluído logicamente). Resulta em HTTP 404. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }

    public static RecursoNaoEncontradoException de(String recurso, Object id) {
        return new RecursoNaoEncontradoException(recurso + " " + id + " não encontrado(a).");
    }
}
