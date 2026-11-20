package br.com.nexusbuilderlab.semanexus.docia.assistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.docia.documento.Documento;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoSpecification;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Busca do assistente (Issue #120): radicais, peso do título e corte dos pouco relevantes. */
class BuscaDocumentosTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DocumentoRepository documentos = mock(DocumentoRepository.class);

    private void conteudo(String termo, Long... ids) {
        String p = "%" + termo + "%";
        when(jdbc.queryForList(eq(DocumentoSpecification.SQL_BUSCA), eq(Long.class), eq(p), eq(p), eq(p), eq(p)))
                .thenReturn(List.of(ids));
    }

    private void titulo(String termo, Long... ids) {
        when(jdbc.queryForList(eq(DocumentoSpecification.SQL_BUSCA_TITULO), eq(Long.class), eq("%" + termo + "%")))
                .thenReturn(List.of(ids));
    }

    private Documento doc(long id, String titulo, String texto) {
        Documento d = mock(Documento.class);
        when(d.getId()).thenReturn(id);
        when(d.getTitulo()).thenReturn(titulo);
        when(d.getTextoExtraido()).thenReturn(texto);
        when(documentos.buscarAtivo(id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void documentoPoucoRelevanteFicaDeFora() {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());
        doc(1, "Parecer Técnico — Cáceres", "Análise: a área afetada foi estimada em 120 hectares.");
        doc(4, "Relatório de Monitoramento de Desmatamento — Colniza",
                "Foram detectados 4 alertas em Colniza, totalizando 210 hectares.");
        doc(5, "Guia de Procedimentos", "Atuação em campo para demandas de desmatamento.");
        conteudo("area", 1L);
        conteudo("desmat", 4L, 5L);
        conteudo("colni", 4L);
        titulo("desmat", 4L);
        titulo("colni", 4L);

        List<BuscaDocumentos.Achado> r = new BuscaDocumentos(jdbc, documentos).buscar("área desmatada Colniza", 5);

        // Colniza: 6 pontos; Parecer e Guia: 1 ponto (< 1/3) — fora da lista.
        assertThat(r).extracting(a -> a.documento().getId()).containsExactly(4L);
        assertThat(r.get(0).aderencia()).isEqualTo("alta");
    }

    @Test
    void aderenciaTextualRelativaAoMelhor() {
        assertThat(BuscaDocumentos.aderenciaTextual(6, 6)).isEqualTo("alta");
        assertThat(BuscaDocumentos.aderenciaTextual(3, 6)).isEqualTo("média");
        assertThat(BuscaDocumentos.aderenciaTextual(2, 6)).isEqualTo("baixa");
    }
}
