package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/** Parte vetorial nunca propaga erro: sem modelo, sem trechos ou com falha de SQL, devolve vazio (Issue #141). */
class BuscaVetorialOracleTest {

    private final TrechoRepositorio trechos = mock(TrechoRepositorio.class);
    private final EmbeddingsDeterministicos embeddings = new EmbeddingsDeterministicos();
    private final BuscaVetorialOracle busca = new BuscaVetorialOracle(embeddings, trechos);

    @Test
    void converteDistanciaEmSimilaridade() {
        when(trechos.contar(EmbeddingsDeterministicos.MODELO)).thenReturn(3);
        when(trechos.maisProximos(any(), anyString(), anyInt(), any())).thenReturn(List.of(
                new TrechoRepositorio.TrechoProximo(4L, "Foram detectados 4 alertas em Colniza.", 0.25)));
        float[] v = busca.vetorizar("desmatamento em Colniza").orElseThrow();
        var r = busca.buscar(v, 5, java.util.Set.of(4L)).orElseThrow();
        org.mockito.Mockito.verify(trechos).maisProximos(v, EmbeddingsDeterministicos.MODELO, 5, java.util.Set.of(4L));
        assertThat(r).singleElement().satisfies(a -> {
            assertThat(a.documentoId()).isEqualTo(4L);
            assertThat(a.similaridade()).isEqualTo(0.75);
        });
    }

    @Test
    void semTrechosIndexadosNaoChamaOModelo() {
        when(trechos.contar(anyString())).thenReturn(0);
        assertThat(busca.vetorizar("queimada")).isEmpty();
        assertThat(embeddings.chamadas()).isZero();
    }

    @Test
    void modeloIndisponivelOuErroDeSqlDegradamParaVazio() {
        embeddings.disponivel(false);
        assertThat(busca.vetorizar("queimada")).isEmpty();
        embeddings.disponivel(true);
        assertThat(busca.vetorizar("  ")).isEmpty();
        when(trechos.maisProximos(any(), anyString(), anyInt(), any()))
                .thenThrow(new DataAccessResourceFailureException("ORA-51805"));
        assertThat(busca.buscar(new float[768], 5, null)).isEmpty();
        assertThat(busca.buscar(null, 5, null)).isEmpty();
        when(trechos.contar(anyString())).thenThrow(new DataAccessResourceFailureException("ORA-00942"));
        assertThat(busca.vetorizar("queimada")).isEmpty();
    }
}
