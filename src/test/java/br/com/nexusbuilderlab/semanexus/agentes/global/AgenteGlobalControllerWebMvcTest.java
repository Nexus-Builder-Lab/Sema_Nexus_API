package br.com.nexusbuilderlab.semanexus.agentes.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.LimitadorRequisicoes;
import br.com.nexusbuilderlab.semanexus.security.LimiteRequisicoesFilter;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

/** {@code POST /api/agentes/assistente-global}: todos os perfis autenticados, 401 sem token, 400 sem pergunta. */
@WebMvcTest(controllers = AgenteGlobalController.class)
@Import({SecurityConfig.class, AgenteGlobalControllerWebMvcTest.Config.class})
class AgenteGlobalControllerWebMvcTest {

    private static final String URL = "/api/agentes/assistente-global";

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService("segredo-de-teste-com-mais-de-32-caracteres!!", Duration.ofHours(8), Clock.systemUTC());
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;
    @MockitoBean
    UsuarioRepository usuarios;
    @MockitoBean
    AgenteGlobalService service;
    @MockitoBean
    AuditService auditService;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 800L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    private static RespostaAssistenteGlobal resposta() {
        return new RespostaAssistenteGlobal(AgenteGlobalService.AVISO, "DEMONSTRATIVO", null,
                AgenteGlobalService.PREFIXO_DEMO + " Prazos: 1 vencida.",
                List.of(new RespostaAssistenteGlobal.LinkAssistente("TELA", "Controle de prazos", "/flux/prazos")),
                List.of("prazos_criticos"), List.of("Prazos: 1 vencida."), List.of());
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"pergunta\":\"prazos\"}"))
                .andExpect(status().isUnauthorized());
        verify(service, never()).perguntar(any(), any());
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void todosOsPerfisAutenticadosPerguntam(Perfil perfil) throws Exception {
        when(service.perguntar(any(), eq("prazos vencidos"))).thenReturn(resposta());
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, bearer(perfil))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pergunta\":\"prazos vencidos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("DEMONSTRATIVO"))
                .andExpect(jsonPath("$.links[0].url").value("/flux/prazos"));
        verify(service).perguntar(argThat(u -> u.perfil() == perfil), eq("prazos vencidos"));
    }

    @Test
    void perguntaVaziaOuLongaRetorna400ProblemJson() throws Exception {
        String token = bearer(Perfil.FISCAL);
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pergunta\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\":\"" + "a".repeat(AgenteGlobalService.MAX_PERGUNTA + 1) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).perguntar(any(), any());
    }

    @Test
    void entraNaCategoriaIaDoLimiteDeRequisicoes() {
        assertThat(LimiteRequisicoesFilter.categoria("POST", URL)).isEqualTo(LimitadorRequisicoes.Categoria.IA);
        assertThat(LimiteRequisicoesFilter.categoria("GET", URL)).isNull();
    }
}
