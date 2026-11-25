package br.com.nexusbuilderlab.semanexus.agentes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.docia.assistente.AssistenteController;
import br.com.nexusbuilderlab.semanexus.docia.assistente.AssistenteService;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.RespostaAssistente;
import br.com.nexusbuilderlab.semanexus.flux.agente.AgenteTriagemController;
import br.com.nexusbuilderlab.semanexus.flux.agente.AgenteTriagemService;
import br.com.nexusbuilderlab.semanexus.flux.agente.SugestaoTriagem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DecisaoTriagem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.LimitadorRequisicoes;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Streaming SSE dos agentes (Issue #135) pela cadeia de seguranÃ§a real: ordem dos eventos, fallback, erro sanitizado,
 * 401/403, JWT sÃ³ no cabeÃ§alho, limite de requisiÃ§Ãµes e desconexÃ£o do cliente.
 */
@WebMvcTest(controllers = {AgenteTriagemController.class, AssistenteController.class})
@Import({SecurityConfig.class, StreamAgentesWebMvcTest.Config.class})
class StreamAgentesWebMvcTest {

    static final String TRIAGEM = "/api/demandas/7/triagem/sugestao/stream";
    static final String DOCIA = "/api/docia/perguntas/stream";
    static final Pattern EVENTO = Pattern.compile("(?m)^event:(\\S+)$");

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService("segredo-de-teste-com-mais-de-32-caracteres!!", Duration.ofHours(8),
                    Clock.systemUTC());
        }

        @Bean
        LimitadorRequisicoes limitadorRequisicoes() {
            return new LimitadorRequisicoes(true, 50, 3, 50, Duration.ofSeconds(60), Clock.systemUTC());
        }

        @Bean
        TransmissaoAgente transmissaoAgente() {
            AgentesConfiguracao config = new AgentesConfiguracao("ollama", "", "claude-haiku-5-5",
                    "claude-sonnet-5-5", "http://ollama:11434", "qwen3:4b", false, 30, 6);
            return new TransmissaoAgente(config, Duration.ofMillis(50), 4);
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;
    @Autowired
    TransmissaoAgente transmissao;
    @MockitoBean
    UsuarioRepository usuarios;
    @MockitoBean
    AuditService auditService;
    @MockitoBean
    AgenteTriagemService triagem;
    @MockitoBean
    AssistenteService assistente;

    private String bearer(Perfil perfil, long id) {
        Usuario u = new Usuario("UsuÃ¡rio Teste", perfil.name().toLowerCase() + id + "@exemplo.test", "hash", perfil);
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    private static SugestaoTriagem sugestao(String modo) {
        return new SugestaoTriagem(modo, null, DecisaoTriagem.CLASSIFICAR, null, null, null,
                "Relato indica desmatamento recente.", List.of(), List.of("Leu o relato."), List.of(), null);
    }

    private static List<String> eventos(String corpo) {
        List<String> r = new ArrayList<>();
        Matcher m = EVENTO.matcher(corpo);
        while (m.find()) {
            r.add(m.group(1));
        }
        return r;
    }

    private String transmitir(String url, String auth, String corpo) throws Exception {
        var req = post(url).header(HttpHeaders.AUTHORIZATION, auth).accept(MediaType.TEXT_EVENT_STREAM);
        if (corpo != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        MvcResult r = mvc.perform(req).andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn();
        r.getAsyncResult(5_000);
        // O despacho ASYNC do fim do stream passa pela cadeia de seguranÃ§a sem exigir o JWT de novo.
        mvc.perform(asyncDispatch(r)).andExpect(status().isOk());
        assertThat(r.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        return r.getResponse().getContentAsString();
    }

    @Test
    void triagemTransmiteEventosEmOrdemEOMesmoPayloadDoJson() throws Exception {
        when(triagem.verificar(any(), eq(7L))).thenReturn(mock(DemandaDetalhe.class));
        when(triagem.sugerir(any(), any(DemandaDetalhe.class), any())).thenAnswer(inv -> {
            ObservadorAgente obs = inv.getArgument(2);
            obs.emitir(EventoAgente.ferramentaIniciada("obter_demanda", 1));
            obs.emitir(EventoAgente.ferramentaConcluida("obter_demanda", 1, "Leu a demanda DEM-2026/0007.", false));
            obs.emitir(EventoAgente.ferramentaIniciada("alertas_inpe_municipio", 2));
            obs.emitir(EventoAgente.ferramentaConcluida("alertas_inpe_municipio", 2,
                    "Erro ERRO_INTERNO: Falha interna ao executar a ferramenta. (ref. ERR-1234abcd)", true));
            return sugestao("LOCAL");
        });

        String corpo = transmitir(TRIAGEM, bearer(Perfil.ANALISTA, 501), null);

        assertThat(eventos(corpo)).containsExactly("inicio", "ferramenta_iniciada", "ferramenta_concluida",
                "ferramenta_iniciada", "ferramenta_concluida", "delta_texto", "concluido");
        assertThat(corpo).contains("\"agente\":\"triagem\"")
                .contains("\"texto\":\"Relato indica desmatamento recente.\"")
                .contains("\"modo\":\"LOCAL\"").contains("\"decisao\":\"CLASSIFICAR\"")
                .contains("ERRO_INTERNO").doesNotContain("Exception");
    }

    @Test
    void docIaComFallbackDemonstrativo() throws Exception {
        when(assistente.documentosPermitidos(any(), any())).thenReturn(null);
        when(assistente.responder(any(), any(), any())).thenAnswer(inv -> {
            ObservadorAgente obs = inv.getArgument(2);
            obs.emitir(EventoAgente.fallbackDemonstrativo("IA indisponÃ­vel; resposta pelo modo extrativo."));
            return new RespostaAssistente("Trechos encontrados nos documentos.", "Modo demonstrativo", List.of());
        });

        String corpo = transmitir(DOCIA, bearer(Perfil.CONSULTA, 502), "{\"pergunta\":\"Qual a Ã¡rea desmatada?\"}");

        assertThat(eventos(corpo)).containsExactly("inicio", "fallback_demonstrativo", "delta_texto", "concluido");
        assertThat(corpo).contains("\"agente\":\"docia\"").contains("\"agente\":\"DEMONSTRATIVO\"");
    }

    @Test
    void falhaInesperadaViraEventoErroSemDetalheInterno() throws Exception {
        when(triagem.verificar(any(), eq(7L))).thenReturn(mock(DemandaDetalhe.class));
        when(triagem.sugerir(any(), any(DemandaDetalhe.class), any()))
                .thenThrow(new IllegalStateException("ORA-00942: tabela SEGREDO nÃ£o existe"));

        String corpo = transmitir(TRIAGEM, bearer(Perfil.GESTOR, 503), null);

        assertThat(eventos(corpo)).containsExactly("inicio", "erro");
        assertThat(corpo).contains("\"referencia\":\"ERR-")
                .doesNotContain("ORA-00942").doesNotContain("SEGREDO");
    }

    @Test
    void semTokenOuComTokenNaUrlRetorna401() throws Exception {
        mvc.perform(post(TRIAGEM)).andExpect(status().isUnauthorized());
        mvc.perform(post(DOCIA).contentType(MediaType.APPLICATION_JSON).content("{\"pergunta\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        // JWT nunca Ã© aceito na query string.
        String token = bearer(Perfil.ADMIN, 504).substring("Bearer ".length());
        mvc.perform(post(TRIAGEM + "?access_token=" + token + "&token=" + token))
                .andExpect(status().isUnauthorized());
        verify(triagem, never()).verificar(any(), any());
    }

    @Test
    void perfisSemTriagemRecebem403() throws Exception {
        long id = 510;
        for (Perfil p : List.of(Perfil.FISCAL, Perfil.CONSULTA)) {
            mvc.perform(post(TRIAGEM).header(HttpHeaders.AUTHORIZATION, bearer(p, id++)))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        verify(triagem, never()).verificar(any(), any());
    }

    @Test
    void escopoNegadoVira404ProblemAntesDoStream() throws Exception {
        when(triagem.verificar(any(), eq(7L))).thenThrow(new RecursoNaoEncontradoException("Demanda nÃ£o encontrada."));
        mvc.perform(post(TRIAGEM).header(HttpHeaders.AUTHORIZATION, bearer(Perfil.ANALISTA, 520))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        when(assistente.documentosPermitidos(any(), any())).thenReturn(Set.of());
        // Corpo invÃ¡lido: 400 como no endpoint JSON.
        mvc.perform(post(DOCIA).header(HttpHeaders.AUTHORIZATION, bearer(Perfil.ANALISTA, 521))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pergunta\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void streamUsaOLimiteDaCategoriaIa() throws Exception {
        when(assistente.documentosPermitidos(any(), any())).thenReturn(null);
        when(assistente.responder(any(), any(), any()))
                .thenReturn(new RespostaAssistente("ok", "Modo demonstrativo", List.of()));
        String auth = bearer(Perfil.ANALISTA, 530);
        for (int i = 0; i < 3; i++) {
            transmitir(DOCIA, auth, "{\"pergunta\":\"Qual a Ã¡rea?\"}");
        }
        mvc.perform(post(DOCIA).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pergunta\":\"Qual a Ã¡rea?\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.categoria").value("IA"));
    }

    @Test
    void desconexaoDoClienteCancelaAExecucao() throws Exception {
        CountDownLatch iniciou = new CountDownLatch(1);
        CountDownLatch parou = new CountDownLatch(1);
        AtomicBoolean viuCancelamento = new AtomicBoolean();
        when(triagem.verificar(any(), eq(7L))).thenReturn(mock(DemandaDetalhe.class));
        when(triagem.sugerir(any(), any(DemandaDetalhe.class), any())).thenAnswer(inv -> {
            ObservadorAgente obs = inv.getArgument(2);
            iniciou.countDown();
            try {
                long limite = System.currentTimeMillis() + 5_000;
                while (!obs.cancelado() && System.currentTimeMillis() < limite) {
                    Thread.sleep(20); // simula o agente trabalhando (o executor checa entre etapas)
                }
                viuCancelamento.set(obs.cancelado());
                throw new ExecucaoCanceladaException();
            } finally {
                parou.countDown();
            }
        });

        MvcResult r = mvc.perform(post(TRIAGEM).header(HttpHeaders.AUTHORIZATION, bearer(Perfil.ADMIN, 540))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(iniciou.await(5, TimeUnit.SECONDS)).isTrue();
        MockAsyncContext ctx = (MockAsyncContext) r.getRequest().getAsyncContext();
        for (AsyncListener l : ctx.getListeners()) {
            l.onError(new AsyncEvent(ctx, new IOException("Broken pipe")));
        }

        assertThat(parou.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(viuCancelamento).isTrue();
        assertThat(eventos(r.getResponse().getContentAsString())).doesNotContain("concluido");
    }
}
