package br.com.nexusbuilderlab.semanexus.docia.documento;

/** Arquivo acima do limite de 10 MB (HTTP 413). */
public class ArquivoMuitoGrandeException extends RuntimeException {
    public ArquivoMuitoGrandeException(String mensagem) {
        super(mensagem);
    }
}
