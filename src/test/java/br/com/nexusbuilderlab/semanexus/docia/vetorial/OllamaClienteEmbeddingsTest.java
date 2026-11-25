package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Cliente de embeddings do Ollama: prefixos, validação da dimensão e degradação (Issue #141). */
class OllamaClienteEmbeddingsTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://ollama:11434");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final RestClient http = builder.build();

    private OllamaClienteEmbeddings cliente(boolean ativo) {
        return new OllamaClienteEmbeddings(new EmbeddingsConfiguracao(ativo, "nomic-embed-text",
                "http://ollama:11434", 60, 0.55, 120), http, http);
    }

    private static String vetor(int dimensao) {
        return IntStream.range(0, dimensao).mapToObj(i -> i == 0 ? "3" : i == 1 ? "4" : "0")
                .collect(Collectors.joining(",", "[", "]"));
    }

    @Test
    void geraVetoresNormalizadosComPrefixoDaConsulta() {
        servidor.expect(requestTo("http://ollama:11434/api/embed"))
                .andExpect(content().json("{\"model\":\"nomic-embed-text\",\"input\":[\"search_query: queimada\"]}"))
                .andRespond(withSuccess("{\"embeddings\":[" + vetor(768) + "]}", MediaType.APPLICATION_JSON));
        List<float[]> r = cliente(true).gerar(List.of("queimada"), ClienteEmbeddings.Finalidade.CONSULTA, true);
        assertThat(r).hasSize(1);
        assertThat(r.get(0)).hasSize(768);
        assertThat(r.get(0)[0]).isEqualTo(0.6f);
        servidor.verify();
    }

    @Test
    void dimensaoDiferenteDe768EhRecusadaEPausaAsChamadas() {
        servidor.expect(requestTo("http://ollama:11434/api/embed"))
                .andRespond(withSuccess("{\"embeddings\":[" + vetor(384) + "]}", MediaType.APPLICATION_JSON));
        OllamaClienteEmbeddings c = cliente(true);
        assertThatThrownBy(() -> c.gerar(List.of("x"), ClienteEmbeddings.Finalidade.DOCUMENTO, false))
                .isInstanceOf(ClienteEmbeddings.EmbeddingsIndisponiveisException.class);
        assertThat(c.disponivel()).isFalse();
        // Em pausa: nem chega a chamar o Ollama (o servidor falso não espera 2ª requisição).
        assertThatThrownBy(() -> c.gerar(List.of("x"), ClienteEmbeddings.Finalidade.CONSULTA, true))
                .isInstanceOf(ClienteEmbeddings.EmbeddingsIndisponiveisException.class);
        servidor.verify();
    }

    @Test
    void erroDoServidorViraIndisponivelSemExporCorpo() {
        servidor.expect(requestTo("http://ollama:11434/api/embed")).andRespond(withServerError().body("segredo"));
        assertThatThrownBy(() -> cliente(true).gerar(List.of("x"), ClienteEmbeddings.Finalidade.CONSULTA, true))
                .isInstanceOf(ClienteEmbeddings.EmbeddingsIndisponiveisException.class)
                .hasMessageNotContaining("segredo");
    }

    @Test
    void desligadoNaoChamaARede() {
        OllamaClienteEmbeddings c = cliente(false);
        assertThat(c.disponivel()).isFalse();
        assertThatThrownBy(() -> c.gerar(List.of("x"), ClienteEmbeddings.Finalidade.CONSULTA, true))
                .isInstanceOf(ClienteEmbeddings.EmbeddingsIndisponiveisException.class);
        servidor.verify();
    }

    @Test
    void configuracaoInvalidaVoltaAoPadrao() {
        EmbeddingsConfiguracao c = new EmbeddingsConfiguracao(true, "modelo inválido!", "ftp://x", 0, 7, 1);
        assertThat(c.modelo()).isEqualTo(EmbeddingsConfiguracao.MODELO_PADRAO);
        assertThat(c.ollamaUrl()).isEqualTo("http://ollama:11434");
        assertThat(c.rrfK()).isEqualTo(1);
        assertThat(c.similaridadeMinima()).isEqualTo(1.0);
        assertThat(c.reindexarSegundos()).isEqualTo(10);
    }
}
