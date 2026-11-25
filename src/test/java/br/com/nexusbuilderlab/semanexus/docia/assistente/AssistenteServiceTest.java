package br.com.nexusbuilderlab.semanexus.docia.assistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.ReferenciaDocumento;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.RespostaAssistente;
import br.com.nexusbuilderlab.semanexus.docia.documento.Documento;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoSpecification;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AssistenteServiceTest {

    private JdbcTemplate jdbc;
    private DocumentoRepository repo;
    private AssistenteService service;

    @BeforeEach
    void preparar() {
        jdbc = mock(JdbcTemplate.class);
        repo = mock(DocumentoRepository.class);
        service = new AssistenteService(jdbc, repo, new DemoAssistenteProvider());
    }

    private Documento doc(String titulo, String texto) {
        return new Documento(titulo, "Descrição", "Relatório", "PDF", LocalDate.now(), null, "queimadas",
                "a.txt", "text/plain", 10, "k", "h", texto, null);
    }

    @Test
    void respondeComTrechoCitadoNoModoDemonstrativo() {
        Documento d = doc("Relatório de queimadas", "Introdução geral. As queimadas no Pantanal cresceram em setembro. Fim.");
        when(jdbc.queryForList(eq(DocumentoSpecification.SQL_BUSCA), eq(Long.class), anyString(), anyString(),
                anyString(), anyString())).thenReturn(List.of(7L));
        when(repo.buscarAtivo(7L)).thenReturn(Optional.of(d));

        RespostaAssistente r = service.perguntar("O que o relatório diz sobre queimadas no Pantanal?");

        assertThat(r.modo()).isEqualTo(DemoAssistenteProvider.MODO).contains("sem IA generativa");
        assertThat(r.referencias()).hasSize(1);
        assertThat(r.referencias().get(0).trecho()).isEqualTo("As queimadas no Pantanal cresceram em setembro.");
        assertThat(r.resposta()).contains("As queimadas no Pantanal cresceram em setembro.");
    }

    @Test
    void semBaseRetornaSemInformacaoSuficienteSemChamarProvider() {
        AssistenteProvider provider = mock(AssistenteProvider.class);
        service = new AssistenteService(jdbc, repo, provider);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());

        RespostaAssistente r = service.perguntar("Qual a cor do cavalo de Napoleão?");

        assertThat(r.resposta()).isEqualTo(AssistenteService.SEM_INFORMACAO);
        assertThat(r.modo()).isEqualTo(DemoAssistenteProvider.MODO);
        assertThat(r.referencias()).isEmpty();
        verify(provider, never()).responder(anyString(), any());
    }

    @Test
    void recusaLegislacaoMultasEProcedimentosOficiais() {
        for (String p : List.of("Qual o valor da multa por desmatamento?", "O que diz a legislação ambiental?",
                "Qual o procedimento oficial de licenciamento?")) {
            RespostaAssistente r = service.perguntar(p);
            assertThat(r.resposta()).isEqualTo(AssistenteService.RECUSA);
            assertThat(r.modo()).isNotBlank();
            assertThat(r.referencias()).isEmpty();
        }
        verify(jdbc, never()).queryForList(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    void perguntaSoComPalavrasVaziasNaoPesquisa() {
        RespostaAssistente r = service.perguntar("O que é isso?");
        assertThat(r.resposta()).isEqualTo(AssistenteService.SEM_INFORMACAO);
        verify(jdbc, never()).queryForList(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    void providerDemoCitaTodosOsTrechos() {
        List<ReferenciaDocumento> ctx = List.of(new ReferenciaDocumento(1L, "A", "t1"),
                new ReferenciaDocumento(2L, "B", "t2"));
        RespostaAssistente r = new DemoAssistenteProvider().responder("x", ctx);
        assertThat(r.referencias()).isEqualTo(ctx);
        assertThat(r.resposta()).contains("[A] t1").contains("[B] t2");
    }

    @Test
    void trechoLongoETruncado() {
        Documento d = doc("T", "queimadas " + "x".repeat(1000));
        String t = AssistenteService.melhorTrecho(d, List.of("queimadas"));
        assertThat(t.length()).isLessThanOrEqualTo(AssistenteService.MAX_TRECHO + 1);
    }

    @Test
    void streamEmiteFallbackQuandoOAgenteFalhaECancelamentoNaoCaiNoExtrativo() {
        AgenteDocia agente = mock(AgenteDocia.class);
        BuscaDocumentos busca = mock(BuscaDocumentos.class);
        when(agente.disponivel()).thenReturn(true);
        when(agente.responder(anyString(), any(), any(), any()))
                .thenThrow(new br.com.nexusbuilderlab.semanexus.agentes.AgenteIndisponivelException(
                        "Falha ao chamar o modelo (HttpServerErrorException)."));
        AssistenteService s = new AssistenteService(busca, new DemoAssistenteProvider(), agente);
        List<br.com.nexusbuilderlab.semanexus.agentes.EventoAgente> eventos = new java.util.ArrayList<>();

        RespostaAssistente r = s.responder(new br.com.nexusbuilderlab.semanexus.docia.assistente.dto.PerguntaRequest("O que diz o relatório de queimadas?"), null,
                eventos::add);

        assertThat(r.resposta()).isEqualTo(AssistenteService.SEM_INFORMACAO); // busca vazia no modo extrativo
        assertThat(eventos).extracting(br.com.nexusbuilderlab.semanexus.agentes.EventoAgente::tipo)
                .containsExactly("fallback_demonstrativo");
        assertThat(eventos.get(0).dados().toString()).doesNotContain("HttpServerErrorException");

        org.mockito.Mockito.doThrow(new br.com.nexusbuilderlab.semanexus.agentes.ExecucaoCanceladaException())
                .when(agente).responder(anyString(), any(), any(), any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> s.responder(
                        new br.com.nexusbuilderlab.semanexus.docia.assistente.dto.PerguntaRequest("O que diz o relatório de queimadas?"), null, eventos::add))
                .isInstanceOf(br.com.nexusbuilderlab.semanexus.agentes.ExecucaoCanceladaException.class);
    }
}
