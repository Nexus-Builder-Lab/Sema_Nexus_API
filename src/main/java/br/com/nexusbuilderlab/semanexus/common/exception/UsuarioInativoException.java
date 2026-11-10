package br.com.nexusbuilderlab.semanexus.common.exception;

/** Credenciais corretas, porém o usuário está inativo. → HTTP 403. */
public class UsuarioInativoException extends RuntimeException {

    public UsuarioInativoException() {
        super("Usuário inativo. Contate o administrador do sistema.");
    }
}
