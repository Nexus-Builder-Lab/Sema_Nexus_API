package br.com.nexusbuilderlab.semanexus.core.sistema;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

@WebMvcTest(controllers = PainelSistemaController.class)
@Import({SecurityConfig.class, PainelSistemaControllerWebMvcTest.Config.class})
class PainelSistemaControllerWebMvcTest {

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
    PainelSistemaService service;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 300L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    @Test
    void somenteAdminAcessa() throws Exception {
        for (Perfil p : List.of(Perfil.GESTOR, Perfil.FISCAL, Perfil.ANALISTA, Perfil.CONSULTA)) {
            mvc.perform(get("/api/sistema/painel").header(HttpHeaders.AUTHORIZATION, bearer(p)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/sistema/painel")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminRecebeOPainel() throws Exception {
        when(service.montar()).thenReturn(new PainelSistema(LocalDateTime.now(),
                new PainelSistema.Sistema("0.1.0", "demo", "21", LocalDateTime.now(), 10, 100, 512),
                List.of(new PainelSistema.Verificacao("banco", "Banco de dados (Oracle)", PainelSistema.Estado.OK, 3L,
                        "Conectado.", Map.of("demandas", 120))),
                List.of()));
        mvc.perform(get("/api/sistema/painel").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificacoes[0].estado").value("OK"))
                .andExpect(jsonPath("$.verificacoes[0].detalhes.demandas").value(120))
                .andExpect(jsonPath("$.sistema.perfilAtivo").value("demo"));
    }
}
