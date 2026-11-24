package br.com.nexusbuilderlab.semanexus.flux.agente;

import static org.mockito.ArgumentMatchers.any;
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

import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** Matriz de perfis do {@code POST /api/demandas/{id}/minuta-vistoria} (Issue #137). */
@WebMvcTest(controllers = AgenteMinutaController.class)
@Import({SecurityConfig.class, AgenteMinutaControllerWebMvcTest.Config.class})
class AgenteMinutaControllerWebMvcTest {

    private static final String URL = "/api/demandas/4/minuta-vistoria";

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
    AgenteMinutaService service;
    @MockitoBean
    AuditService auditService;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 700L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    private static MinutaVistoria minuta() {
        return new MinutaVistoria(AgenteMinutaService.AVISO, "DEMONSTRATIVO", null, 4L, "DEM-2026/0004",
                "minuta-vistoria-DEM-2026-0004.md", "**" + AgenteMinutaService.AVISO + "**\n\n# Minuta", List.of(),
                List.of());
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(post(URL)).andExpect(status().isUnauthorized());
        verify(service, never()).gerar(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = Perfil.class, names = {"ADMIN", "GESTOR", "ANALISTA", "FISCAL"})
    void perfisAutorizadosRecebemAMinuta(Perfil perfil) throws Exception {
        when(service.gerar(any(), eq(4L))).thenReturn(minuta());
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, bearer(perfil)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aviso").value("MINUTA GERADA PARA REVISÃO HUMANA — AMBIENTE DEMONSTRATIVO"))
                .andExpect(jsonPath("$.modo").value("DEMONSTRATIVO"))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.startsWith(
                        "**MINUTA GERADA PARA REVISÃO HUMANA")));
    }

    @Test
    void consultaRecebe403ProblemJson() throws Exception {
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, bearer(Perfil.CONSULTA)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verify(service, never()).gerar(any(), any());
    }

    @Test
    void fiscalNaoDesignadoRecebe403ProblemJson() throws Exception {
        when(service.gerar(any(), eq(4L))).thenThrow(new AccessDeniedException("Somente o fiscal designado."));
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, bearer(Perfil.FISCAL)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void demandaInexistenteRetorna404() throws Exception {
        when(service.gerar(any(), eq(77L))).thenThrow(RecursoNaoEncontradoException.de("Demanda", 77L));
        mvc.perform(post("/api/demandas/77/minuta-vistoria").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.GESTOR)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
