package br.com.nexusbuilderlab.semanexus.core.usuario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.usuario.dto.UsuarioDto;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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

@WebMvcTest(controllers = UsuarioController.class)
@Import({SecurityConfig.class, UsuarioControllerWebMvcTest.Config.class})
class UsuarioControllerWebMvcTest {

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
    UsuarioService service;
    @MockitoBean
    AuditService auditService;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 300L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    private static final String CORPO = "{\"nome\":\"Novo\",\"email\":\"novo@exemplo.test\",\"senha\":\"%s\","
            + "\"perfil\":\"FISCAL\"}";

    @Test
    void senhaCurtaRetorna400() throws Exception {
        mvc.perform(post("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO.formatted("1234567")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("senha"));
        verify(service, never()).criar(any());
    }

    @Test
    void naoAdminRecebe403NasEscritas() throws Exception {
        for (Perfil p : List.of(Perfil.GESTOR, Perfil.FISCAL, Perfil.ANALISTA, Perfil.CONSULTA)) {
            String token = bearer(p);
            mvc.perform(post("/api/usuarios").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(CORPO.formatted("12345678")))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/usuarios/1/ativacao").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void consultaSomenteAdminEGestor() throws Exception {
        when(service.listar(any(), any(), any(), any())).thenReturn(new PageResponse<UsuarioDto>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.GESTOR)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.FISCAL)))
                .andExpect(status().isForbidden());
    }

    @Test
    void desativarUltimoAdminRetorna409() throws Exception {
        when(service.alterarAtivacao(1L, false)).thenThrow(new ConflitoException("último admin"));
        mvc.perform(patch("/api/usuarios/1/ativacao").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCriaUsuarioRetorna201() throws Exception {
        when(service.criar(any())).thenReturn(new UsuarioDto(10L, "Novo", "novo@exemplo.test", Perfil.FISCAL, null,
                null, true, null, null));
        mvc.perform(post("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO.formatted("12345678")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }
}
