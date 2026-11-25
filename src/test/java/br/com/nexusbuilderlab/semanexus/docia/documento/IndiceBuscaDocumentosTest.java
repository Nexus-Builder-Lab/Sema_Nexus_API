package br.com.nexusbuilderlab.semanexus.docia.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Issue #164: normalização da coluna TEXTO_BUSCA e SQL da busca sem função sobre o CLOB. */
class IndiceBuscaDocumentosTest {

    JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void textoBuscaFicaMinusculoSemAcentosEPreservaTamanhoDeTextosLongos() {
        assertThat(DocumentoSpecification.textoBusca("QUEIMADA em Cáceres — AUTUAÇÃO nº 12; Ñandu"))
                .isEqualTo("queimada em caceres — autuacao nº 12; nandu");
        assertThat(DocumentoSpecification.textoBusca(null)).isNull();
        String longo = "Ação de fiscalização ".repeat(3000); // ~ 63 KB em UTF-8
        String normal = DocumentoSpecification.textoBusca(longo);
        assertThat(normal).hasSize(longo.length()).startsWith("acao de fiscalizacao ");
        // o termo normalizado do mesmo modo casa com o texto normalizado (mesma regra dos dois lados)
        String padrao = DocumentoSpecification.padraoBusca("FISCALIZAÇÃO");
        assertThat(normal).contains(padrao.substring(1, padrao.length() - 1));
    }

    @Test
    void sqlDaBuscaNaoAplicaFuncaoNoClob() {
        assertThat(DocumentoSpecification.SQL_BUSCA)
                .contains("TEXTO_BUSCA LIKE ? ESCAPE '\\'")
                .doesNotContain("TEXTO_EXTRAIDO")
                .doesNotContain("TRANSLATE(TEXTO_BUSCA")
                .doesNotContain("LOWER(");
        assertThat(DocumentoSpecification.SQL_BUSCA.chars().filter(c -> c == '?').count()).isEqualTo(4);
    }

    @Test
    void preencherPendentesGravaTextoNormalizadoSoDosSemColuna() {
        when(jdbc.queryForList(IndiceBuscaDocumentos.SQL_PENDENTES, Long.class)).thenReturn(List.of(41L, 42L));
        when(jdbc.queryForObject(IndiceBuscaDocumentos.SQL_TEXTO, String.class, 41L)).thenReturn("Minuta — QUEIMADA");
        when(jdbc.queryForObject(IndiceBuscaDocumentos.SQL_TEXTO, String.class, 42L)).thenReturn(null);

        assertThat(new IndiceBuscaDocumentos(jdbc).preencherPendentes()).isEqualTo(1);
        verify(jdbc).update(IndiceBuscaDocumentos.SQL_ATUALIZAR, "minuta — queimada", 41L);
        verify(jdbc, never()).update(IndiceBuscaDocumentos.SQL_ATUALIZAR, null, 42L);
    }

    @Test
    void falhaNoPreenchimentoNaoImpedeASubida() {
        when(jdbc.queryForList(anyString(), any(Class.class))).thenThrow(new IllegalStateException("banco fora"));
        new IndiceBuscaDocumentos(jdbc).run(null);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
