package br.com.nexusbuilderlab.semanexus.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import br.com.nexusbuilderlab.semanexus.docia.assistente.BuscaDocumentos;
import br.com.nexusbuilderlab.semanexus.docia.documento.Documento;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoSpecification;
import br.com.nexusbuilderlab.semanexus.docia.vetorial.BuscaVetorialMemoria;
import br.com.nexusbuilderlab.semanexus.docia.vetorial.EmbeddingsDeterministicos;
import br.com.nexusbuilderlab.semanexus.docia.vetorial.Recuperacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Benchmark offline da busca do DocIA (Issue #141): textual × híbrida no acervo fictício do seed
 * ({@link DemoMassaDocumentos}) e no conjunto fixo de perguntas {@code benchmark/perguntas-busca.json} (o mesmo do
 * {@code scripts/benchmark-busca.*}, que mede com o modelo real). A busca lexical roda o código de produção com o SQL
 * emulado em memória (mesma normalização sem acento/caixa); a vetorial usa o gerador de embeddings
 * <b>determinístico falso</b> ({@link EmbeddingsDeterministicos}), que só aproxima palavras e pedaços de palavras.
 * Mede precisão@k, acerto@1 e abstenção; grava a tabela em {@code target/benchmark-busca.md}. Os números deste teste
 * provam a mecânica (fusão, limiar, abstenção), não a qualidade semântica do nomic-embed-text.
 */
class BenchmarkBuscaTest {

    /** Limiar do gerador falso (cossenos de bag-of-trigramas são mais baixos que os do modelo real). */
    static final double LIMIAR_FALSO = 0.30;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DocumentoRepository repositorio = mock(DocumentoRepository.class);
    private final Map<Long, String> titulos = new HashMap<>();
    private final Map<Long, String> textoBusca = new HashMap<>();
    private final Map<Long, String> tituloNormal = new HashMap<>();
    private final EmbeddingsDeterministicos embeddings = new EmbeddingsDeterministicos();
    private final BuscaVetorialMemoria vetorial = new BuscaVetorialMemoria(embeddings);

    record Metricas(double precisao, double acertoTop1, double recall, double abstencaoCorreta, double abstencaoIndevida,
                    int respondiveis, int foraDoAcervo) {
    }

    @BeforeEach
    void carregarAcervo() {
        long id = 1;
        for (DemoMassaDocumentos.DocSeed s : DemoMassaDocumentos.catalogo()) {
            long docId = id++;
            String texto = DemoMassaDocumentos.AVISO + "\n\n" + s.titulo() + "\n\n" + s.texto() + "\n\n"
                    + DemoMassaDocumentos.AVISO;
            Documento d = mock(Documento.class);
            when(d.getId()).thenReturn(docId);
            when(d.getTitulo()).thenReturn(s.titulo());
            when(d.getDescricao()).thenReturn(s.descricao());
            when(d.getPalavrasChave()).thenReturn(s.palavrasChave());
            when(d.getTextoExtraido()).thenReturn(texto);
            when(repositorio.buscarAtivo(docId)).thenReturn(Optional.of(d));
            titulos.put(docId, s.titulo());
            tituloNormal.put(docId, DocumentoSpecification.normalizar(s.titulo()));
            textoBusca.put(docId, DocumentoSpecification.normalizar(String.join(" ", s.titulo(), s.descricao(),
                    s.palavrasChave(), texto)));
            vetorial.indexar(docId, s.titulo(), s.descricao(), s.palavrasChave(), texto);
        }
        // SQL de produção emulado: LIKE '%termo%' sobre os campos normalizados.
        when(jdbc.queryForList(eq(DocumentoSpecification.SQL_BUSCA), eq(Long.class), any(Object[].class)))
                .thenAnswer(i -> ids(textoBusca, i.getArgument(2)));
        when(jdbc.queryForList(eq(DocumentoSpecification.SQL_BUSCA_TITULO), eq(Long.class), anyString()))
                .thenAnswer(i -> ids(tituloNormal, i.getArgument(2)));
    }

    private static List<Long> ids(Map<Long, String> campos, Object padrao) {
        String termo = String.valueOf(padrao).replace("%", "").replace("\\", "");
        return campos.entrySet().stream().filter(e -> e.getValue().contains(termo)).map(Map.Entry::getKey)
                .sorted().toList();
    }

    static JsonNode perguntas() throws IOException {
        try (InputStream in = BenchmarkBuscaTest.class.getResourceAsStream("/benchmark/perguntas-busca.json")) {
            return new ObjectMapper().readTree(in);
        }
    }

    Metricas medir(BuscaDocumentos busca, boolean hibrida, int k, JsonNode conjunto, StringBuilder detalhe) {
        double somaPrecisao = 0;
        double somaRecall = 0;
        int acertos1 = 0;
        int respondiveis = 0;
        int fora = 0;
        int abstencoesCorretas = 0;
        int abstencoesIndevidas = 0;
        for (JsonNode p : conjunto.path("perguntas")) {
            List<String> relevantes = new ArrayList<>();
            p.path("relevantes").forEach(r -> relevantes.add(r.asText()));
            BuscaDocumentos.Resultado r = busca.pesquisar(p.path("pergunta").asText(), k, null, hibrida);
            if (hibrida) {
                assertThat(r.recuperacao()).isEqualTo(Recuperacao.HIBRIDA);
            }
            List<String> top = r.achados().stream().map(a -> titulos.get(a.documento().getId())).toList();
            detalhe.append("| ").append(hibrida ? "híbrida" : "lexical").append(" | ").append(p.path("pergunta").asText())
                    .append(" | ").append(top.isEmpty() ? "(abstenção)" : String.join("; ", top)).append(" |\n");
            if (relevantes.isEmpty()) {
                fora++;
                if (top.isEmpty()) {
                    abstencoesCorretas++;
                }
                continue;
            }
            respondiveis++;
            if (top.isEmpty()) {
                abstencoesIndevidas++;
                continue;
            }
            long certos = top.stream().filter(relevantes::contains).count();
            somaPrecisao += (double) certos / top.size();
            somaRecall += (double) certos / Math.min(k, relevantes.size());
            if (relevantes.contains(top.get(0))) {
                acertos1++;
            }
        }
        return new Metricas(somaPrecisao / respondiveis, (double) acertos1 / respondiveis, somaRecall / respondiveis,
                fora == 0 ? 1 : (double) abstencoesCorretas / fora, (double) abstencoesIndevidas / respondiveis,
                respondiveis, fora);
    }

    private static String linha(String nome, Metricas m) {
        return String.format(Locale.ROOT, "| %s | %.2f | %.2f | %.2f | %.2f | %.2f |%n", nome, m.precisao(),
                m.acertoTop1(), m.recall(), m.abstencaoCorreta(), m.abstencaoIndevida());
    }

    @Test
    void lexicalVersusHibridaNoAcervoFicticio() throws IOException {
        JsonNode conjunto = perguntas();
        int k = conjunto.path("k").asInt(3);
        BuscaDocumentos busca = new BuscaDocumentos(jdbc, repositorio, vetorial, 60, LIMIAR_FALSO);
        StringBuilder detalhe = new StringBuilder();
        Metricas lexical = medir(busca, false, k, conjunto, detalhe);
        Metricas hibrida = medir(busca, true, k, conjunto, detalhe);
        String tabela = "| Recuperação | Precisão@" + k + " | Acerto@1 | Recall@" + k
                + " | Abstenção correta | Abstenção indevida |\n|---|---|---|---|---|---|\n"
                + linha("LEXICAL", lexical) + linha("HIBRIDA (embeddings falsos, limiar " + LIMIAR_FALSO + ")", hibrida);
        Path saida = Path.of("target", "benchmark-busca.md");
        Files.createDirectories(saida.getParent());
        Files.writeString(saida, "# Benchmark offline da busca (Issue #141)\n\n" + lexical.respondiveis()
                + " perguntas respondíveis, " + lexical.foraDoAcervo() + " fora do acervo.\n\n" + tabela
                + "\n| Modo | Pergunta | Top-" + k + " |\n|---|---|---|\n" + detalhe, StandardCharsets.UTF_8);
        System.out.println(tabela);

        // A híbrida não perde o que a lexical já acerta no 1º lugar e preserva a abstenção.
        assertThat(hibrida.acertoTop1()).isGreaterThanOrEqualTo(lexical.acertoTop1());
        assertThat(hibrida.abstencaoCorreta()).isGreaterThanOrEqualTo(lexical.abstencaoCorreta());
        assertThat(hibrida.abstencaoIndevida()).isLessThanOrEqualTo(lexical.abstencaoIndevida());
    }

    @Test
    void semEmbeddingsAHibridaDegradaParaOLexicalIdentico() throws IOException {
        JsonNode conjunto = perguntas();
        BuscaDocumentos busca = new BuscaDocumentos(jdbc, repositorio, vetorial, 60, LIMIAR_FALSO);
        embeddings.disponivel(false);
        for (JsonNode p : conjunto.path("perguntas")) {
            String q = p.path("pergunta").asText();
            BuscaDocumentos.Resultado auto = busca.pesquisar(q, 3, null, true);
            BuscaDocumentos.Resultado lexical = busca.pesquisar(q, 3, null, false);
            assertThat(auto.recuperacao()).isEqualTo(Recuperacao.LEXICAL);
            assertThat(auto.achados()).extracting(a -> a.documento().getId())
                    .containsExactlyElementsOf(lexical.achados().stream().map(a -> a.documento().getId()).toList());
        }
    }
}
