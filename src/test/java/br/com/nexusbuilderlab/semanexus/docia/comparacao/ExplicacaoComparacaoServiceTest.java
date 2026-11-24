package br.com.nexusbuilderlab.semanexus.docia.comparacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.agentes.AgentesConfiguracao;
import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo;
import br.com.nexusbuilderlab.semanexus.agentes.ExecutorAgente;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ExplicacaoComparacaoResponse;
import br.com.nexusbuilderlab.semanexus.docia.documento.Documento;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExplicacaoComparacaoServiceTest {

    private static final String V1 = "Parecer 014/2026.\nA área afetada foi estimada em 120 hectares. A outorga prevê "
            + "vazão de 15 L/s por 12 meses, valor de R$ 1.500,00, até 10/12/2026, com 30% de redução.\n"
            + "Conclusão preliminar: recomenda-se vistoria em campo.";
    private static final String V2 = "Parecer 014/2026.\nA área afetada foi corrigida para 95 hectares. A outorga "
            + "prevê vazão de 20 L/s por 12 meses, valor de R$ 2.000,00, até 15/01/2027, com 30% de redução.\n"
            + "Conclusão: recomenda-se a lavratura de notificação.";

    private final DocumentoRepository repo = mock(DocumentoRepository.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AgentesConfiguracao config = new AgentesConfiguracao("ollama", "", "m", "m",
            "http://ollama:11434", "qwen3:4b-instruct", false, 30, 6);

    static class Roteiro implements ClienteModelo {
        boolean disponivel = true;
        String resposta = "{\"resumo\":\"A área caiu de 120 para 95 hectares.\"}";
        boolean falha;

        public boolean disponivel() {
            return disponivel;
        }

        public String modelo(String sugerido) {
            return "qwen3:4b-instruct";
        }

        public RespostaModelo criar(PedidoModelo pedido) {
            if (falha) {
                throw new br.com.nexusbuilderlab.semanexus.agentes.AgenteIndisponivelException("falhou");
            }
            return new RespostaModelo("stop", List.of(), resposta, null, 10, 5, 0);
        }
    }

    private static Documento doc(String titulo, String texto) {
        return new Documento(titulo, null, "Parecer técnico", "Parecer", LocalDate.of(2026, 10, 1), null, "k",
                "a.pdf", "application/pdf", 10, "key", "hash" + titulo, texto, null);
    }

    private ExplicacaoComparacaoService servico(Roteiro modelo) {
        when(repo.buscarAtivo(1L)).thenReturn(Optional.of(doc("v1", V1)));
        when(repo.buscarAtivo(2L)).thenReturn(Optional.of(doc("v2", V2)));
        when(repo.buscarAtivo(3L)).thenReturn(Optional.of(doc("vazio", " ")));
        when(repo.buscarAtivo(99L)).thenReturn(Optional.empty());
        return new ExplicacaoComparacaoService(new ComparacaoService(repo),
                new ExecutorAgente(modelo, config, mock(AuditService.class), json), config);
    }

    @Test
    void extraiGrandezasDeterministicasEPreservaIguais() {
        GrandezasExtrator.Resultado r = GrandezasExtrator.comparar(V1, V2);
        assertThat(r.grandezas()).anySatisfy(g -> {
            assertThat(g.categoria()).isEqualTo("AREA");
            assertThat(g.valorA()).isEqualTo("120 hectares");
            assertThat(g.valorB()).isEqualTo("95 hectares");
            assertThat(g.variacaoAbsoluta()).isEqualTo(-25.0);
            assertThat(g.variacaoPercentual()).isEqualTo(-20.83);
        });
        assertThat(r.grandezas()).extracting(g -> g.categoria())
                .contains("VAZAO", "VALOR", "DATA", "CONCLUSAO").doesNotContain("PRAZO", "PERCENTUAL");
        assertThat(r.grandezas()).anySatisfy(g -> {
            assertThat(g.categoria()).isEqualTo("VALOR");
            assertThat(g.variacaoAbsoluta()).isEqualTo(500.0);
        });
        assertThat(r.iguais()).isGreaterThanOrEqualTo(2); // 12 meses e 30%
    }

    @Test
    void numerosBrasileirosEMultiplicadores() {
        assertThat(GrandezasExtrator.numero("1.234,5")).isEqualTo(1234.5);
        assertThat(GrandezasExtrator.numero("1.500")).isEqualTo(1500.0);
        assertThat(GrandezasExtrator.extrair("Custo de R$ 2 milhões e 3,5 ha.")).extracting(i -> i.valor())
                .contains(2_000_000.0, 3.5);
    }

    @Test
    void comIaOResumoVemDoModeloMasATabelaSoDaExtracao() {
        Roteiro m = new Roteiro();
        ExplicacaoComparacaoResponse r = servico(m).explicar(1L, 2L);
        assertThat(r.modo()).isEqualTo("LOCAL");
        assertThat(r.resumoGeradoPorIa()).isTrue();
        assertThat(r.resumo()).contains("95");
        assertThat(r.grandezas()).isNotEmpty();
    }

    @Test
    void semIaOuComFalhaUsaRegrasIdentificadasComoDemonstrativo() {
        Roteiro off = new Roteiro();
        off.disponivel = false;
        ExplicacaoComparacaoResponse r = servico(off).explicar(1L, 2L);
        assertThat(r.modo()).isEqualTo("DEMONSTRATIVO");
        assertThat(r.resumoGeradoPorIa()).isFalse();
        assertThat(r.resumo()).contains("120 hectares").contains("95 hectares");

        Roteiro erro = new Roteiro();
        erro.falha = true;
        assertThat(servico(erro).explicar(1L, 2L).modo()).isEqualTo("DEMONSTRATIVO");
    }

    @Test
    void acessoAosDoisDocumentos() {
        ExplicacaoComparacaoService s = servico(new Roteiro());
        assertThatThrownBy(() -> s.explicar(1L, 99L)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> s.explicar(3L, 1L)).isInstanceOf(DadosInvalidosException.class);
    }

    @Test
    void documentosIguaisNaoTemGrandezasAlteradas() {
        ExplicacaoComparacaoResponse r = servico(new Roteiro()).explicar(1L, 1L);
        assertThat(r.grandezas()).isEmpty();
        assertThat(r.modo()).isEqualTo("DEMONSTRATIVO");
    }
}
