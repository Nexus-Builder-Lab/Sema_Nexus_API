package br.com.nexusbuilderlab.semanexus.security;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.core.auth.AuthController;
import br.com.nexusbuilderlab.semanexus.core.auth.AuthService;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginResponse;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.UsuarioResumo;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** 429 problem+json com Retry-After no login acima do limite por IP (Issue #134), pela cadeia de segurança real. */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, LimiteRequisicoesWebMvcTest.Config.class})
class LimiteRequisicoesWebMvcTest {

    static final String CORPO = "{\"email\":\"fulano@exemplo.test\",\"senha\":\"senha-de-teste-123\"}";

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService("segredo-de-teste-com-mais-de-32-caracteres!!", Duration.ofHours(8),
                    Clock.systemUTC());
        }

        @Bean
        LimitadorRequisicoes limitadorRequisicoes() {
            return new LimitadorRequisicoes(true, 2, 10, 20, Duration.ofSeconds(60), Clock.systemUTC());
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService authService;
    @MockitoBean
    UsuarioRepository usuarios;

    @Test
    void loginAcimaDoLimitePorIpRetorna429ComRetryAfter() throws Exception {
        Usuario u = new Usuario("Fulano Fictício", "fulano@exemplo.test", "hash", Perfil.GESTOR);
        ReflectionTestUtils.setField(u, "id", 3L);
        when(authService.login(any())).thenReturn(new LoginResponse("token", 28_800, UsuarioResumo.de(u)));

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("198.51.100.20"); return r; })
                            .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("198.51.100.20"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Limite de requisições excedido"))
                .andExpect(jsonPath("$.categoria").value("LOGIN"))
                .andExpect(jsonPath("$.retryAfterSegundos").isNumber())
                .andExpect(jsonPath("$.detail").value(containsString("Tente novamente")));

        // Outro IP não é afetado.
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("198.51.100.21"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk());
    }
}
