package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Busca vetorial em memória para testes offline: mesmos trechos do {@link Fatiador} e mesma entrada de embedding do
 * {@link IndexadorTrechos} (título + trecho), cosseno calculado em Java no lugar do {@code VECTOR_DISTANCE} do Oracle.
 */
public class BuscaVetorialMemoria implements BuscaVetorial {

    private record Trecho(long documentoId, String texto, float[] vetor) {
    }

    private final ClienteEmbeddings embeddings;
    private final List<Trecho> trechos = new ArrayList<>();

    public BuscaVetorialMemoria(ClienteEmbeddings embeddings) {
        this.embeddings = embeddings;
    }

    public void indexar(long id, String titulo, String descricao, String palavras, String texto) {
        List<String> partes = Fatiador.fatiar(titulo, descricao, palavras, texto);
        List<String> entradas = new ArrayList<>();
        for (int i = 0; i < partes.size(); i++) {
            entradas.add(i == 0 ? partes.get(i) : titulo + "\n" + partes.get(i));
        }
        List<float[]> v = embeddings.gerar(entradas, ClienteEmbeddings.Finalidade.DOCUMENTO, false);
        for (int i = 0; i < partes.size(); i++) {
            trechos.add(new Trecho(id, partes.get(i), v.get(i)));
        }
    }

    @Override
    public Optional<float[]> vetorizar(String consulta) {
        if (!embeddings.disponivel() || trechos.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(embeddings.gerar(List.of(consulta), ClienteEmbeddings.Finalidade.CONSULTA, true)
                    .get(0));
        } catch (ClienteEmbeddings.EmbeddingsIndisponiveisException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<List<AchadoVetorial>> buscar(float[] q, int maxDocumentos, java.util.Set<Long> ids) {
        Map<Long, AchadoVetorial> melhor = new LinkedHashMap<>();
        for (Trecho t : trechos) {
            if (ids != null && !ids.contains(t.documentoId())) {
                continue;
            }
            double s = Vetores.cosseno(q, t.vetor());
            AchadoVetorial atual = melhor.get(t.documentoId());
            if (atual == null || s > atual.similaridade()) {
                melhor.put(t.documentoId(), new AchadoVetorial(t.documentoId(), t.texto(), s));
            }
        }
        return Optional.of(melhor.values().stream()
                .sorted(Comparator.comparingDouble(AchadoVetorial::similaridade).reversed()
                        .thenComparingLong(AchadoVetorial::documentoId))
                .limit(maxDocumentos).toList());
    }
}
