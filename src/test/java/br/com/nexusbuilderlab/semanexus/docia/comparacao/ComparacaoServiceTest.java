package br.com.nexusbuilderlab.semanexus.docia.comparacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ComparacaoResponse;
import br.com.nexusbuilderlab.semanexus.docia.documento.Documento;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ComparacaoServiceTest {

    private static final String V1 = "Parecer Técnico nº 014/2026 (versão 1).\nAssunto: queimada em pastagem, "
            + "município de Cáceres, bioma Pantanal.\n\nAnálise: a área afetada foi estimada em 120 hectares com "
            + "base em imagens de satélite simuladas.\n\nConclusão preliminar: recomenda-se vistoria em campo.";
    private static final String V2 = "Parecer Técnico nº 014/2026 (versão 2, revisada).\nAssunto: queimada em "
            + "pastagem, município de Cáceres, bioma Pantanal.\n\nAnálise: a área afetada foi corrigida para 95 "
            + "hectares com base em imagens de satélite simuladas.\n\nConclusão: recomenda-se a lavratura de "
            + "notificação.";

    private final DocumentoRepository repo = mock(DocumentoRepository.class);
    private final ComparacaoService service = new ComparacaoService(repo);

    private static Documento doc(String titulo, String texto) {
        return new Documento(titulo, null, "Parecer técnico", "Parecer", LocalDate.of(2026, 10, 1), null, "k",
                "a.pdf", "application/pdf", 10, "key", "hash" + titulo, texto, null);
    }

    @Test
    void diffMostraExatamenteOQueMudouNasVersoesDoParecer() {
        when(repo.buscarAtivo(1L)).thenReturn(Optional.of(doc("v1", V1)));
        when(repo.buscarAtivo(2L)).thenReturn(Optional.of(doc("v2", V2)));

        ComparacaoResponse r = service.comparar(1L, 2L);

        assertThat(r.geradoPorIa()).isFalse();
        assertThat(r.aviso()).contains("não gerada por IA");
        assertThat(r.diffPalavras()).anySatisfy(t -> {
            assertThat(t.tipo()).isEqualTo("REMOVIDO");
            assertThat(t.texto()).contains("estimada em 120");
        });
        assertThat(r.diffPalavras()).anySatisfy(t -> {
            assertThat(t.tipo()).isEqualTo("ADICIONADO");
            assertThat(t.texto()).contains("corrigida para 95");
        });
        assertThat(r.termosExclusivosA()).contains("120", "estimada", "preliminar");
        assertThat(r.termosExclusivosB()).contains("95", "corrigida", "lavratura");
        assertThat(r.termosExclusivosA()).doesNotContain("pastagem");
        assertThat(r.similaridade()).isBetween(50.0, 95.0);
        assertThat(r.metadados()).anySatisfy(m -> {
            assertThat(m.campo()).isEqualTo("Tipo");
            assertThat(m.igual()).isTrue();
        });
        assertThat(r.metadados()).anySatisfy(m -> {
            assertThat(m.campo()).isEqualTo("Título");
            assertThat(m.igual()).isFalse();
        });
        assertThat(r.trechosSemelhantes()).isNotEmpty();
        assertThat(r.diffLinhas()).extracting(l -> l.tipo()).contains("REMOVIDO", "ADICIONADO");
    }

    @Test
    void textosIguaisTemSimilaridade100SemDiferencas() {
        when(repo.buscarAtivo(1L)).thenReturn(Optional.of(doc("x", V1)));
        when(repo.buscarAtivo(2L)).thenReturn(Optional.of(doc("x", V1)));

        ComparacaoResponse r = service.comparar(1L, 2L);

        assertThat(r.similaridade()).isEqualTo(100.0);
        assertThat(r.diffPalavras()).allMatch(t -> t.tipo().equals("IGUAL"));
        assertThat(r.termosExclusivosA()).isEmpty();
        assertThat(r.termosExclusivosB()).isEmpty();
    }

    @Test
    void documentoSemTextoExtraidoGeraMensagemClara() {
        when(repo.buscarAtivo(1L)).thenReturn(Optional.of(doc("v1", V1)));
        when(repo.buscarAtivo(2L)).thenReturn(Optional.of(doc("scan", "  ")));

        assertThatThrownBy(() -> service.comparar(1L, 2L)).isInstanceOf(DadosInvalidosException.class)
                .satisfies(e -> assertThat(((DadosInvalidosException) e).getErros().get(0).mensagem())
                        .contains("scan").contains("não possui texto extraído"));
    }

    @Test
    void documentoInexistenteLanca404() {
        when(repo.buscarAtivo(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.comparar(9L, 2L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void lcsDePalavrasIgnoraCaixaAcentoEPontuacao() {
        List<TextoDiff.Op> ops = TextoDiff.diff(TextoDiff.palavras("A área, afetada."),
                TextoDiff.palavras("a AREA afetada muito"));
        assertThat(ops).extracting(TextoDiff.Op::tipo).containsExactly("IGUAL", "IGUAL", "IGUAL", "ADICIONADO");
    }

    @Test
    void agrupaOperacoesConsecutivas() {
        List<TextoDiff.Op> g = TextoDiff.agrupar(TextoDiff.diff(TextoDiff.palavras("um dois tres quatro"),
                TextoDiff.palavras("um cinco seis quatro")));
        assertThat(g).extracting(TextoDiff.Op::tipo)
                .containsExactly("IGUAL", "REMOVIDO", "ADICIONADO", "IGUAL");
        assertThat(g.get(1).texto()).isEqualTo("dois tres");
        assertThat(g.get(2).texto()).isEqualTo("cinco seis");
    }

    @Test
    void jaccardCalculaInterseccaoSobreUniao() {
        assertThat(TextoDiff.jaccard(TextoDiff.termos("onça pintada rio"), TextoDiff.termos("onça rio mata")))
                .isEqualTo(50.0);
    }
}
