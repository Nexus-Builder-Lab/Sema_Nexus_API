package br.com.nexusbuilderlab.semanexus.core.auditoria;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.AuditoriaDto;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuditoriaController.class)
@Import({SecurityConfig.class, AuditoriaControllerWebMvcTest.Config.class})
class AuditoriaControllerWebMvcTest {

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
    AuditService auditService;
    @MockitoBean
    IntegridadeAuditoriaService integridade;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 200L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    @Test
    void naoAdminRecebe403() throws Exception {
        for (Perfil p : List.of(Perfil.GESTOR, Perfil.FISCAL, Perfil.ANALISTA, Perfil.CONSULTA)) {
            mvc.perform(get("/api/auditoria").header(HttpHeaders.AUTHORIZATION, bearer(p)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void semTokenRecebe401() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminConsultaComFiltros() throws Exception {
        var dto = new AuditoriaDto(1L, LocalDateTime.now(), 5L, "Fulano", "CRIAR", "DEMANDA", "42", "d");
        when(auditService.consultar(any(), any())).thenReturn(new PageResponse<>(List.of(dto), 0, 20, 1, 1));

        mvc.perform(get("/api/auditoria?de=2026-11-01&ate=2026-11-30&acao=CRIAR&entidade=DEMANDA&usuarioId=5")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Perfil.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].acao").value("CRIAR"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void integridadeSoParaAdmin() throws Exception {
        for (Perfil p : List.of(Perfil.GESTOR, Perfil.FISCAL, Perfil.ANALISTA, Perfil.CONSULTA)) {
            mvc.perform(get("/api/auditoria/integridade").header(HttpHeaders.AUTHORIZATION, bearer(p)))
                    .andExpect(status().isForbidden())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .contentTypeCompatibleWith("application/problem+json"));
        }
        mvc.perform(get("/api/auditoria/integridade")).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(integridade);
    }

    @Test
    void adminConfereIntegridade() throws Exception {
        var falha = new br.com.nexusbuilderlab.semanexus.core.auditoria.dto.IntegridadeAuditoria.FalhaIntegridade(
                3L, 42L, "HASH_DIVERGENTE", "alterado");
        when(integridade.verificar()).thenReturn(new br.com.nexusbuilderlab.semanexus.core.auditoria.dto
                .IntegridadeAuditoria(false, "VIOLADA", "SHA-256", 2, 5, 0, 7, "ab", falha, LocalDateTime.now()));

        mvc.perform(get("/api/auditoria/integridade").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integra").value(false))
                .andExpect(jsonPath("$.situacao").value("VIOLADA"))
                .andExpect(jsonPath("$.registrosLegados").value(5))
                .andExpect(jsonPath("$.primeiraFalha.auditoriaId").value(42))
                .andExpect(jsonPath("$.primeiraFalha.motivo").value("HASH_DIVERGENTE"));
    }

    @Test
    void listaExpoeAntesDepoisEHash() throws Exception {
        var dto = new AuditoriaDto(1L, LocalDateTime.now(), 5L, "Fulano", "EDITAR", "DEMANDA", "42", "d",
                java.util.Map.of("titulo", "A"), java.util.Map.of("titulo", "B"), 9L, "f".repeat(64));
        when(auditService.consultar(any(), any())).thenReturn(new PageResponse<>(List.of(dto), 0, 20, 1, 1));

        mvc.perform(get("/api/auditoria").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].valorAnterior.titulo").value("A"))
                .andExpect(jsonPath("$.content[0].valorNovo.titulo").value("B"))
                .andExpect(jsonPath("$.content[0].seqCadeia").value(9));
    }
}
