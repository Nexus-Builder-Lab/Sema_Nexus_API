package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Preenchimento idempotente e resiliente dos trechos (Issue #141). */
class IndexadorTrechosTest {

    private final TrechoRepositorio trechos = mock(TrechoRepositorio.class);
    private final EmbeddingsDeterministicos embeddings = new EmbeddingsDeterministicos();
    private final IndexadorTrechos indexador = new IndexadorTrechos(embeddings, trechos,
            EmbeddingsConfiguracao.padrao(), new MockEnvironment());

    private void documento(long id) {
        when(trechos.documento(id)).thenReturn(Optional.of(new TrechoRepositorio.DocumentoTexto(id,
                "Relatório " + id, "Descrição fictícia", "queimada", "Texto fictício do documento " + id + ".")));
    }

    @Test
    void indexaOsPendentesEParaQuandoNaoHaMais() {
        documento(1);
        documento(2);
        when(trechos.pendentes(EmbeddingsDeterministicos.MODELO, IndexadorTrechos.LOTE_PENDENTES))
                .thenReturn(List.of(1L, 2L), List.of());
        assertThat(indexador.preencher()).isEqualTo(2);
        verify(trechos).substituir(eq(1L), eq(EmbeddingsDeterministicos.MODELO),
                eq(List.of("Relatório 1\nDescrição fictícia\nqueimada", "Texto fictício do documento 1.")), anyList());
        verify(trechos).substituir(eq(2L), anyString(), anyList(), anyList());
    }

    @Test
    void documentoQueContinuaPendenteNaoPrendeOLaco() {
        documento(1);
        when(trechos.pendentes(anyString(), anyInt())).thenReturn(List.of(1L));
        assertThat(indexador.preencher()).isEqualTo(1);
    }

    @Test
    void semEmbeddingsNaoGravaENemConsulta() {
        embeddings.disponivel(false);
        assertThat(indexador.preencher()).isZero();
        verify(trechos, never()).pendentes(anyString(), anyInt());
        verify(trechos, never()).substituir(anyLong(), anyString(), anyList(), anyList());
    }

    @Test
    void falhaNoMeioDeixaPendenteParaOProximoCiclo() {
        documento(1);
        when(trechos.pendentes(anyString(), anyInt())).thenReturn(List.of(1L));
        EmbeddingsDeterministicos falha = new EmbeddingsDeterministicos() {
            @Override
            public List<float[]> gerar(List<String> t, Finalidade f, boolean i) {
                throw new EmbeddingsIndisponiveisException("caiu");
            }
        };
        IndexadorTrechos ix = new IndexadorTrechos(falha, trechos, EmbeddingsConfiguracao.padrao(),
                new MockEnvironment());
        assertThat(ix.preencher()).isZero();
        assertThat(ix.indexar(1L)).isFalse();
        verify(trechos, never()).substituir(anyLong(), anyString(), anyList(), anyList());
    }

    @Test
    void documentoExcluidoNaoEhIndexado() {
        when(trechos.documento(9L)).thenReturn(Optional.empty());
        assertThat(indexador.indexar(9L)).isTrue();
        verify(trechos, never()).substituir(anyLong(), anyString(), anyList(), anyList());
    }
}
