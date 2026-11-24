package br.com.nexusbuilderlab.semanexus.core.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.common.exception.CredenciaisInvalidasException;
import br.com.nexusbuilderlab.semanexus.common.exception.UsuarioInativoException;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.AuthConfigResponse;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginRequest;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginResponse;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.UsuarioResumo;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.RevogacaoTokenService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fatia web: login, proteção de endpoints (401/403 em problem+json) e leitura do Bearer token. */
@WebMvcTest(controllers = {AuthController.class, AuthControllerWebMvcTest.ProtegidoController.class})
@Import({SecurityConfig.class, AuthControllerWebMvcTest.Config.class, AuthControllerWebMvcTest.ProtegidoController.class})
class AuthControllerWebMvcTest {

    static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres!!";
    static final String SENHA_TESTE = "senha-de-teste-123";
    static final String PROBLEM_JSON = "application/problem+json";

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService(SEGREDO, Duration.ofHours(8), Clock.systemUTC());
        }
    }

    /** Endpoint fictício só para exercitar @PreAuthorize por perfil. */
    @RestController
    static class ProtegidoController {
        @GetMapping("/api/teste/somente-admin")
        @PreAuthorize("hasRole('ADMIN')")
        String somenteAdmin() {
            return "ok";
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;

    @MockitoBean
    AuthService authService;
    @MockitoBean
    UsuarioRepository usuarios;
    @MockitoBean
    RevogacaoTokenService revogacao;

    private static Usuario usuario(long id, Perfil perfil, boolean ativo) {
        Usuario u = new Usuario("Fulano Fictício", "fulano@exemplo.test", "hash", perfil);
        ReflectionTestUtils.setField(u, "id", id);
        u.setAtivo(ativo);
        return u;
    }

    private String bearer(Usuario u) {
        when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    @Test
    void loginValidoRetorna200ComTokenEUsuario() throws Exception {
        var resposta = new LoginResponse("jwt.de.teste", 28_800L,
                new UsuarioResumo(1L, "Fulano Fictício", "fulano@exemplo.test", Perfil.ADMIN));
        when(authService.login(any(LoginRequest.class))).thenReturn(resposta);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fulano@exemplo.test\",\"senha\":\"" + SENHA_TESTE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.de.teste"))
                .andExpect(jsonPath("$.expiresIn").value(28_800))
                .andExpect(jsonPath("$.usuario.id").value(1))
                .andExpect(jsonPath("$.usuario.perfil").value("ADMIN"))
                .andExpect(content().string(not(containsString("senha"))));
    }

    @Test
    void credenciaisInvalidasRetorna401ProblemJsonGenerico() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new CredenciaisInvalidasException());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@exemplo.test\",\"senha\":\"errada-123456\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void usuarioInativoRetorna403ComMensagemClara() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new UsuarioInativoException());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@exemplo.test\",\"senha\":\"" + SENHA_TESTE + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Usuário inativo. Contate o administrador do sistema."));
    }

    @Test
    void loginSemCamposRetorna400ComErrosPorCampo() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].campo").value("email"))
                .andExpect(jsonPath("$.errors[1].campo").value("senha"));
    }

    @Test
    void configEPublicoERetornaFlagDemo() throws Exception {
        when(authService.configuracao()).thenReturn(new AuthConfigResponse(true));

        mvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true));
    }

    @Test
    void semTokenRetorna401ProblemJson() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/auth/me"));
    }

    @Test
    void tokenInvalidoRetorna401() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer lixo.lixo.lixo"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoPermiteConsultarMe() throws Exception {
        Usuario u = usuario(3L, Perfil.FISCAL, true);
        when(authService.me(3L)).thenReturn(UsuarioResumo.de(u));

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("fulano@exemplo.test"))
                .andExpect(jsonPath("$.perfil").value("FISCAL"));
    }

    @Test
    void tokenDeUsuarioInativoOuRemovidoRetorna401() throws Exception {
        Usuario inativo = usuario(4L, Perfil.ADMIN, false);
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(inativo)))
                .andExpect(status().isUnauthorized());

        Usuario removido = usuario(5L, Perfil.ADMIN, true);
        String token = "Bearer " + jwtService.gerar(removido);
        when(usuarios.findById(5L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRegistraAuditoriaEDevolve204() throws Exception {
        Usuario u = usuario(6L, Perfil.GESTOR, true);

        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(u)))
                .andExpect(status().isNoContent());

        verify(authService).logout(eq(6L), argThat(jti -> jti != null && !jti.isBlank()), any(Instant.class));
    }

    @Test
    void tokenRevogadoNoLogoutRetorna401() throws Exception {
        Usuario u = usuario(9L, Perfil.ANALISTA, true);
        String token = bearer(u);
        String jti = jwtService.validar(token.substring("Bearer ".length())).orElseThrow().jti();
        when(revogacao.revogado(jti)).thenReturn(true);

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutSemTokenRetorna401() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void perfilSemPermissaoRetorna403ProblemJson() throws Exception {
        Usuario consulta = usuario(7L, Perfil.CONSULTA, true);

        mvc.perform(get("/api/teste/somente-admin").header(HttpHeaders.AUTHORIZATION, bearer(consulta)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    void perfilComPermissaoAcessaEndpointRestrito() throws Exception {
        Usuario admin = usuario(8L, Perfil.ADMIN, true);

        mvc.perform(get("/api/teste/somente-admin").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }
}
