package br.com.nexusbuilderlab.semanexus.flux.agente;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;

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

@WebMvcTest(controllers = AgenteDistribuicaoController.class)
@Import({SecurityConfig.class, AgenteDistribuicaoControllerWebMvcTest.Config.class})
class AgenteDistribuicaoControllerWebMvcTest {

    private static final String URL = "/api/demandas/4/distribuicao/sugestao";

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
    AgenteDistribuicaoService service;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 400L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    @Test
    void somenteAdminEGestor() throws Exception {
        for (Perfil p : List.of(Perfil.FISCAL, Perfil.ANALISTA, Perfil.CONSULTA)) {
            mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, bearer(p))).andExpect(status().isForbidden());
        }
        mvc.perform(post(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void gestorEAdminRecebemASugestao() throws Exception {
        when(service.sugerir(any(), eq(4L))).thenReturn(new SugestaoDistribuicao("DEMONSTRATIVO", null,
                new SugestaoDistribuicao.EquipeSugerida(2L, "Equipe B", "NR-SNO"),
                new SugestaoDistribuicao.FiscalSugerido(20L, "Carla", 1, 0, 0), LocalDate.of(2026, 12, 6),
                "Menor carga.", List.of("Consultou a carga."), List.of()));
        for (Perfil p : List.of(Perfil.ADMIN, Perfil.GESTOR)) {
            mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, bearer(p)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.modo").value("DEMONSTRATIVO"))
                    .andExpect(jsonPath("$.equipe.id").value(2))
                    .andExpect(jsonPath("$.fiscal.id").value(20))
                    .andExpect(jsonPath("$.prazo").value("2026-12-06"));
        }
    }

    @Test
    void statusInvalidoDevolveProblem409() throws Exception {
        when(service.sugerir(any(), eq(4L))).thenThrow(new ConflitoException("fora do status"));
        mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, bearer(Perfil.GESTOR)))
                .andExpect(status().isConflict());
    }
}
