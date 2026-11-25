package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import java.util.List;

/**
 * Gera embeddings (vetores normalizados de {@link EmbeddingsConfiguracao#DIMENSAO} dimensões). Implementações:
 * {@link OllamaClienteEmbeddings} (modelo local) e, nos testes, um gerador determinístico.
 */
public interface ClienteEmbeddings {

    /** Finalidade do texto: o nomic-embed-text usa prefixos distintos para pergunta e documento. */
    enum Finalidade { CONSULTA, DOCUMENTO }

    /**
     * @param interativo {@code true} quando há usuário esperando (timeout curto); {@code false} na indexação
     * @throws EmbeddingsIndisponiveisException modelo/endpoint indisponível, desligado ou resposta inválida
     */
    List<float[]> gerar(List<String> textos, Finalidade finalidade, boolean interativo);

    /** Identificação gravada em {@code DOCUMENTO_TRECHO.MODELO}. */
    String modelo();

    /** Configuração ligada e sem falha recente (não faz chamada de rede). */
    boolean disponivel();

    /** Falha de embedding: o chamador cai na busca lexical. A mensagem nunca traz o corpo da resposta. */
    class EmbeddingsIndisponiveisException extends RuntimeException {
        public EmbeddingsIndisponiveisException(String mensagem) {
            super(mensagem);
        }
    }
}
