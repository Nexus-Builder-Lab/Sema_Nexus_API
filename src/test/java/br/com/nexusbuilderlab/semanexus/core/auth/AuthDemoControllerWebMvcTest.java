package br.com.nexusbuilderlab.semanexus.core.auth;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginResponse;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.UsuarioResumo;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Rota /api/auth/demo/{perfil}: existe só no profile demo (404 nos demais). */
class AuthDemoControllerWebMvcTest {

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService("segredo-de-teste-com-mais-de-32-caracteres!!", Duration.ofHours(8), Clock.systemUTC());
        }
    }

    @Nested
    @WebMvcTest(controllers = AuthDemoController.class)
    @Import({SecurityConfig.class, Config.class})
    @ActiveProfiles("demo")
    class ProfileDemo {
        @Autowired
        MockMvc mvc;
        @MockitoBean
        AuthService authService;
        @MockitoBean
        UsuarioRepository usuarios;

        @Test
        void perfilValidoDevolveLoginResponseSemAutenticacao() throws Exception {
            when(authService.loginDemo("GESTOR")).thenReturn(new LoginResponse("jwt.de.teste", 28_800L,
                    new UsuarioResumo(2L, "Gestor Demonstrativo", "gestor@demo.semaalerta.local", Perfil.GESTOR)));
            mvc.perform(post("/api/auth/demo/GESTOR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt.de.teste"))
                    .andExpect(jsonPath("$.usuario.perfil").value("GESTOR"));
        }

        @Test
        void perfilInvalidoRetorna400ESemUsuarioRetorna404() throws Exception {
            when(authService.loginDemo("XPTO")).thenThrow(new DadosInvalidosException("perfil", "inválido"));
            when(authService.loginDemo("FISCAL")).thenThrow(new RecursoNaoEncontradoException("sem usuário"));
            mvc.perform(post("/api/auth/demo/XPTO")).andExpect(status().isBadRequest());
            mvc.perform(post("/api/auth/demo/FISCAL")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @WebMvcTest(controllers = AuthDemoController.class)
    @Import({SecurityConfig.class, Config.class})
    @ActiveProfiles("dev")
    class ProfileDev {
        @Autowired
        MockMvc mvc;
        @MockitoBean
        AuthService authService;
        @MockitoBean
        UsuarioRepository usuarios;

        @Test
        void rotaNaoExisteForaDoProfileDemo() throws Exception {
            mvc.perform(post("/api/auth/demo/ADMIN")).andExpect(status().isNotFound());
        }
    }
}
