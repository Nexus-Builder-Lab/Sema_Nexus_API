package br.com.nexusbuilderlab.semanexus.docia.assistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import br.com.nexusbuilderlab.semanexus.docia.documento.Documento;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoSpecification;
import br.com.nexusbuilderlab.semanexus.docia.vetorial.BuscaVetorial;
import br.com.nexusbuilderlab.semanexus.docia.vetorial.BuscaVetorial.AchadoVetorial;
import br.com.nexusbuilderlab.semanexus.docia.vetorial.Recuperacao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Busca híbrida (Issue #141): fusão RRF, limiar semântico (abstenção) e degradação para lexical. */
class BuscaHibridaTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DocumentoRepository documentos = mock(DocumentoRepository.class);
    private final BuscaVetorial vetorial = mock(BuscaVetorial.class);
    private final BuscaDocumentos busca = new BuscaDocumentos(jdbc, documentos, vetorial, 60, 0.55);

    private void conteudo(String termo, Long... ids) {
        String p = "%" + termo + "%";
        when(jdbc.queryForList(eq(DocumentoSpecification.SQL_BUSCA), eq(Long.class), eq(p), eq(p), eq(p), eq(p)))
                .thenReturn(List.of(ids));
    }

    private void doc(long id, String titulo, String texto) {
        Documento d = mock(Documento.class);
        when(d.getId()).thenReturn(id);
        when(d.getTitulo()).thenReturn(titulo);
        when(d.getTextoExtraido()).thenReturn(texto);
        when(documentos.buscarAtivo(id)).thenReturn(Optional.of(d));
    }

    @BeforeEach
    void preparar() {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());
        doc(1, "Termo de Denúncia — Sinop", "Relato de descarte de resíduos em terreno baldio.");
        doc(2, "Laudo de Recursos Hídricos", "Captação sem outorga; vazão de 12 litros por segundo.");
        doc(3, "Nota Técnica — Focos de Calor", "Pico de queimadas em setembro, em razão da estiagem.");
    }

    @Test
    void rrfSomaAsPosicoesDosDoisRankings() {
        // Lexical: 1, 2, 3 · vetorial: 3, 2 → 2 (1/62 + 1/62) e 3 (1/63 + 1/61) passam à frente de 1 (1/61).
        assertThat(BuscaDocumentos.fundirRrf(List.of(1L, 2L, 3L), List.of(3L, 2L), 60))
                .containsExactly(3L, 2L, 1L);
        assertThat(BuscaDocumentos.fundirRrf(List.of(1L), List.of(), 60)).containsExactly(1L);
        // Empate exato desempata pelo id.
        assertThat(BuscaDocumentos.fundirRrf(List.of(5L), List.of(4L), 60)).containsExactly(4L, 5L);
    }

    @Test
    void semEmbeddingsOResultadoEhOLexicalDeAntes() {
        conteudo("resid", 1L);
        when(vetorial.buscar(anyString(), anyInt())).thenReturn(Optional.empty());
        BuscaDocumentos.Resultado r = busca.pesquisar("resíduos", 5, null);
        assertThat(r.recuperacao()).isEqualTo(Recuperacao.LEXICAL);
        assertThat(r.achados()).extracting(a -> a.documento().getId()).containsExactly(1L);
        assertThat(r.achados().get(0).aderencia()).isEqualTo("alta");
        assertThat(r.achados().get(0).similaridadeSemantica()).isNull();
        // Mesmo resultado da busca só lexical (construtor sem parte vetorial).
        assertThat(new BuscaDocumentos(jdbc, documentos).pesquisar("resíduos", 5, null).achados())
                .extracting(a -> a.documento().getId()).containsExactly(1L);
    }

    @Test
    void documentoSoSemanticoEntraComTrechoDoVetorEAderenciaNula() {
        // "lixo" não aparece em nenhum texto: o lexical se absteria; o vetorial aproxima do Termo de Denúncia.
        when(vetorial.buscar(anyString(), anyInt())).thenReturn(Optional.of(List.of(
                new AchadoVetorial(1L, "Relato de descarte de resíduos em terreno baldio.", 0.71234),
                new AchadoVetorial(3L, "Pico de queimadas em setembro.", 0.31))));
        BuscaDocumentos.Resultado r = busca.pesquisar("lixo jogado na rua", 5, null);
        assertThat(r.recuperacao()).isEqualTo(Recuperacao.HIBRIDA);
        assertThat(r.achados()).singleElement().satisfies(a -> {
            assertThat(a.documento().getId()).isEqualTo(1L);
            assertThat(a.trecho()).isEqualTo("Relato de descarte de resíduos em terreno baldio.");
            assertThat(a.aderencia()).isNull(); // nenhum termo da consulta: aderência textual não se aplica
            assertThat(a.similaridadeSemantica()).isEqualTo(0.712);
        });
    }

    @Test
    void abaixoDoLimiarNinguemEntraEABuscaSeAbstem() {
        when(vetorial.buscar(anyString(), anyInt())).thenReturn(Optional.of(List.of(
                new AchadoVetorial(2L, "Captação sem outorga.", 0.40),
                new AchadoVetorial(3L, "Pico de queimadas.", 0.38))));
        BuscaDocumentos.Resultado r = busca.pesquisar("preço da arroba do boi", 5, null);
        assertThat(r.recuperacao()).isEqualTo(Recuperacao.HIBRIDA);
        assertThat(r.achados()).isEmpty();
    }

    @Test
    void fusaoMantemAderenciaTextualESomaSimilaridade() {
        conteudo("queima", 3L);
        conteudo("capta", 2L);
        when(jdbc.queryForList(eq(DocumentoSpecification.SQL_BUSCA_TITULO), eq(Long.class), eq("%queima%")))
                .thenReturn(List.of(3L));
        when(vetorial.buscar(anyString(), anyInt())).thenReturn(Optional.of(List.of(
                new AchadoVetorial(3L, "Pico de queimadas em setembro.", 0.80),
                new AchadoVetorial(2L, "Captação sem outorga.", 0.60))));
        BuscaDocumentos.Resultado r = busca.pesquisar("queimadas captação", 5, null);
        assertThat(r.achados()).extracting(a -> a.documento().getId()).containsExactly(3L, 2L);
        assertThat(r.achados()).extracting(BuscaDocumentos.Achado::aderencia).containsExactly("alta", "baixa");
        assertThat(r.achados()).extracting(BuscaDocumentos.Achado::similaridadeSemantica).containsExactly(0.8, 0.6);
    }

    @Test
    void respeitaOsDocumentosPermitidosNaParteVetorial() {
        when(vetorial.buscar(anyString(), anyInt())).thenReturn(Optional.of(List.of(
                new AchadoVetorial(1L, "Relato de descarte.", 0.9),
                new AchadoVetorial(2L, "Captação sem outorga.", 0.8))));
        BuscaDocumentos.Resultado r = busca.pesquisar("lixo", 5, Set.of(2L));
        assertThat(r.achados()).extracting(a -> a.documento().getId()).containsExactly(2L);
    }

    @Test
    void modoLexicalNaoConsultaAParteVetorial() {
        conteudo("resid", 1L);
        BuscaDocumentos.Resultado r = busca.pesquisar("resíduos", 5, null, false);
        assertThat(r.recuperacao()).isEqualTo(Recuperacao.LEXICAL);
        verify(vetorial, never()).buscar(anyString(), anyInt());
    }
}
