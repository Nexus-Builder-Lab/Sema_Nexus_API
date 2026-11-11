package br.com.nexusbuilderlab.semanexus.docia.documento;

/** Extensão não permitida ou conteúdo que não corresponde à extensão (HTTP 415). */
public class ArquivoNaoSuportadoException extends RuntimeException {
    public ArquivoNaoSuportadoException(String mensagem) {
        super(mensagem);
    }
}
