package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Busca vetorial no Oracle 23ai ({@code VECTOR_DISTANCE} de cosseno, exata). Qualquer falha — embeddings
 * indisponíveis, tabela vazia, erro de SQL — devolve vazio e a busca segue só lexical; nunca propaga erro.
 */
@Component
public class BuscaVetorialOracle implements BuscaVetorial {

    private static final Logger LOG = LoggerFactory.getLogger(BuscaVetorialOracle.class);

    private final ClienteEmbeddings embeddings;
    private final TrechoRepositorio trechos;

    public BuscaVetorialOracle(ClienteEmbeddings embeddings, TrechoRepositorio trechos) {
        this.embeddings = embeddings;
        this.trechos = trechos;
    }

    @Override
    public Optional<List<AchadoVetorial>> buscar(String consulta, int maxDocumentos) {
        if (consulta == null || consulta.isBlank() || !embeddings.disponivel()) {
            return Optional.empty();
        }
        try {
            if (trechos.contar(embeddings.modelo()) == 0) {
                return Optional.empty(); // nada indexado ainda: não há o que fundir
            }
            float[] v = embeddings.gerar(List.of(consulta.strip()), ClienteEmbeddings.Finalidade.CONSULTA, true)
                    .get(0);
            return Optional.of(trechos.maisProximos(v, embeddings.modelo(), maxDocumentos).stream()
                    .map(t -> new AchadoVetorial(t.documentoId(), t.texto(), 1.0 - t.distancia()))
                    .toList());
        } catch (ClienteEmbeddings.EmbeddingsIndisponiveisException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            LOG.warn("Busca vetorial indisponível ({}); seguindo só com a busca lexical.", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
