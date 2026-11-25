package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.docia.assistente.BuscaDocumentos;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoService;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoUploadRequest;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Issue #141 contra Oracle 23ai real: a V10 cria {@code DOCUMENTO_TRECHO} com {@code VECTOR(768, FLOAT32)}, a
 * indexação grava os vetores ({@code TO_VECTOR} a partir de CLOB) e a consulta {@code VECTOR_DISTANCE ... COSINE}
 * devolve o trecho mais próximo de cada documento ativo, com a mesma similaridade calculada em Java. Embeddings do
 * gerador determinístico (sem Ollama). Sem embeddings reais, a busca do sistema segue lexical.
 */
@Tag("oracle")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class DocumentoTrechoOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry r) {
        r.add("semanexus.storage.dir", () -> java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                "sema-trechos-" + UUID.randomUUID()).toString());
        r.add("EMBEDDINGS_REINDEXAR_SEGUNDOS", () -> "86400");
    }

    @Autowired
    DocumentoService documentos;
    @Autowired
    DocumentoRepository repositorio;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    TrechoRepositorio trechos;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Environment ambiente;
    @Autowired
    BuscaDocumentos buscaDoSistema;

    private long enviar(UsuarioAutenticado ator, String titulo, String texto) {
        return documentos.enviar(ator, new DocumentoUploadRequest(new MockMultipartFile("arquivo", "doc.txt",
                "text/plain", texto.getBytes(StandardCharsets.UTF_8)), titulo, null, "Relatório", "Laudo",
                LocalDate.now(), null, List.of(), null)).id();
    }

    @Test
    void migrationIndexacaoEConsultaVetorialNoOracle() {
        assertThat(jdbc.queryForObject("SELECT DATA_TYPE FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'DOCUMENTO_TRECHO' "
                + "AND COLUMN_NAME = 'EMBEDDING'", String.class)).isEqualTo("VECTOR");

        Usuario u = usuarios.save(new Usuario("Analista Vetor", "vetor." + UUID.randomUUID() + "@exemplo.test",
                "hash", Perfil.ANALISTA));
        UsuarioAutenticado ator = UsuarioAutenticado.de(u);
        long colniza = enviar(ator, "Monitoramento de desmatamento — Colniza (teste)",
                "Foram detectados alertas de supressão de vegetação em Colniza, totalizando 210 hectares simulados.");
        long sinop = enviar(ator, "Termo de denúncia — Sinop (teste)",
                "Relato simulado de descarte de resíduos em terreno baldio.");

        EmbeddingsDeterministicos falso = new EmbeddingsDeterministicos();
        IndexadorTrechos indexador = new IndexadorTrechos(falso, trechos, EmbeddingsConfiguracao.padrao(), ambiente);
        assertThat(trechos.pendentes(EmbeddingsDeterministicos.MODELO, 1000)).contains(colniza, sinop);
        assertThat(indexador.preencher()).isGreaterThanOrEqualTo(2);
        assertThat(trechos.pendentes(EmbeddingsDeterministicos.MODELO, 1000)).doesNotContain(colniza, sinop);
        int total = trechos.contar(EmbeddingsDeterministicos.MODELO);
        assertThat(indexador.preencher()).isZero(); // idempotente: nada pendente, nada refeito
        assertThat(trechos.contar(EmbeddingsDeterministicos.MODELO)).isEqualTo(total);
        // Reindexar o mesmo documento substitui (não duplica) os trechos.
        assertThat(indexador.indexar(colniza)).isTrue();
        assertThat(trechos.contar(EmbeddingsDeterministicos.MODELO)).isEqualTo(total);

        String consulta = "derrubada da floresta em Colniza";
        BuscaVetorialOracle vetorial = new BuscaVetorialOracle(falso, trechos);
        float[] vq = vetorial.vetorizar(consulta).orElseThrow();
        var achados = vetorial.buscar(vq, 10, null).orElseThrow();
        // Filtro de documentos permitidos no SQL (contexto da demanda).
        assertThat(vetorial.buscar(vq, 10, java.util.Set.of(sinop)).orElseThrow())
                .extracting(BuscaVetorial.AchadoVetorial::documentoId).containsExactly(sinop);
        assertThat(achados.get(0).documentoId()).isEqualTo(colniza);
        assertThat(achados).extracting(BuscaVetorial.AchadoVetorial::documentoId).doesNotHaveDuplicates();
        // Cosseno do Oracle = cosseno em Java (mesmos vetores FLOAT32).
        float[] q = EmbeddingsDeterministicos.vetor(consulta);
        String trecho = achados.get(0).trecho();
        float[] t = EmbeddingsDeterministicos.vetor(trecho.startsWith("Monitoramento") ? trecho
                : "Monitoramento de desmatamento — Colniza (teste)\n" + trecho);
        assertThat(achados.get(0).similaridade()).isCloseTo(Vetores.cosseno(q, t), within(1e-4));

        // Fusão no Oracle: o documento entra pela parte vetorial e a resposta se declara HIBRIDA.
        BuscaDocumentos hibrida = new BuscaDocumentos(jdbc, repositorio, vetorial, 60, 0.0);
        BuscaDocumentos.Resultado r = hibrida.pesquisar(consulta, 3, null);
        assertThat(r.recuperacao()).isEqualTo(Recuperacao.HIBRIDA);
        assertThat(r.achados().get(0).documento().getId()).isEqualTo(colniza);

        // Documento excluído (lógico) sai da busca vetorial.
        jdbc.update("UPDATE DOCUMENTO SET EXCLUIDO_EM = SYSTIMESTAMP WHERE ID = ?", colniza);
        assertThat(vetorial.buscar(vq, 10, null).orElseThrow())
                .extracting(BuscaVetorial.AchadoVetorial::documentoId).doesNotContain(colniza);

        // O sistema real (sem Ollama no teste) nunca falha: degrada para a busca lexical.
        assertThat(buscaDoSistema.pesquisar("resíduos terreno baldio", 3, null).recuperacao())
                .isEqualTo(Recuperacao.LEXICAL);
    }
}
