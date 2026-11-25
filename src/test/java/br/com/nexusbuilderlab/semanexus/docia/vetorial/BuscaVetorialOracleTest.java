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
        when(trechos.maisProximos(any(), anyString(), anyInt())).thenReturn(List.of(
                new TrechoRepositorio.TrechoProximo(4L, "Foram detectados 4 alertas em Colniza.", 0.25)));
        var r = busca.buscar("desmatamento em Colniza", 5).orElseThrow();
        assertThat(r).singleElement().satisfies(a -> {
            assertThat(a.documentoId()).isEqualTo(4L);
            assertThat(a.similaridade()).isEqualTo(0.75);
        });
    }

    @Test
    void semTrechosIndexadosNaoChamaOModelo() {
        when(trechos.contar(anyString())).thenReturn(0);
        assertThat(busca.buscar("queimada", 5)).isEmpty();
        assertThat(embeddings.chamadas()).isZero();
    }

    @Test
    void modeloIndisponivelOuErroDeSqlDegradamParaVazio() {
        embeddings.disponivel(false);
        assertThat(busca.buscar("queimada", 5)).isEmpty();
        embeddings.disponivel(true);
        when(trechos.contar(anyString())).thenThrow(new DataAccessResourceFailureException("ORA-00942"));
        assertThat(busca.buscar("queimada", 5)).isEmpty();
        assertThat(busca.buscar("  ", 5)).isEmpty();
    }
}
