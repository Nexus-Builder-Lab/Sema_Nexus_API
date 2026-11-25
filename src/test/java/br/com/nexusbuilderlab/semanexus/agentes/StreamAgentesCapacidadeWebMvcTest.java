package br.com.nexusbuilderlab.semanexus.agentes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.docia.assistente.AssistenteController;
import br.com.nexusbuilderlab.semanexus.docia.assistente.AssistenteService;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.RespostaAssistente;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Capacidade de streams esgotada (Issue #135): 503 {@code problem+json} imediato, sem abrir o stream. */
@WebMvcTest(controllers = AssistenteController.class)
@Import({SecurityConfig.class, StreamAgentesCapacidadeWebMvcTest.Config.class})
class StreamAgentesCapacidadeWebMvcTest {

    static final String DOCIA = "/api/docia/perguntas/stream";
    static final String CORPO = "{\"pergunta\":\"Qual a área desmatada?\"}";

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService("segredo-de-teste-com-mais-de-32-caracteres!!", Duration.ofHours(8),
                    Clock.systemUTC());
        }

        @Bean
        TransmissaoAgente transmissaoAgente() {
            AgentesConfiguracao config = new AgentesConfiguracao("ollama", "", "claude-haiku-5-5",
                    "claude-sonnet-5-5", "http://ollama:11434", "qwen3:4b", false, 30, 6);
            return new TransmissaoAgente(config, Duration.ofSeconds(5), 1);
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;
    @MockitoBean
    UsuarioRepository usuarios;
    @MockitoBean
    AuditService auditService;
    @MockitoBean
    AssistenteService assistente;

    @Test
    void acimaDaCapacidadeResponde503ProblemJson() throws Exception {
        Usuario u = new Usuario("Usuário Teste", "analista@exemplo.test", "hash", Perfil.ANALISTA);
        ReflectionTestUtils.setField(u, "id", 601L);
        when(usuarios.findById(601L)).thenReturn(Optional.of(u));
        String auth = "Bearer " + jwtService.gerar(u);
        CountDownLatch ocupado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        when(assistente.documentosPermitidos(any(), any())).thenReturn(null);
        when(assistente.responder(any(), any(), any())).thenAnswer(inv -> {
            ocupado.countDown();
            liberar.await(5, TimeUnit.SECONDS);
            return new RespostaAssistente("ok", "Modo demonstrativo", List.of());
        });

        MvcResult primeiro = mvc.perform(post(DOCIA).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(ocupado.await(5, TimeUnit.SECONDS)).isTrue();

        mvc.perform(post(DOCIA).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(503));

        liberar.countDown();
        primeiro.getAsyncResult(5_000);
        assertThat(primeiro.getResponse().getContentAsString()).contains("event:concluido");
    }
}
