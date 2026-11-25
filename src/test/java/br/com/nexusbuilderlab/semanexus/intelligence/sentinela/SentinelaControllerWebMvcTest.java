package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

@WebMvcTest(controllers = SentinelaController.class)
@Import({SecurityConfig.class, SentinelaControllerWebMvcTest.Config.class})
class SentinelaControllerWebMvcTest {

    private static final String URL = "/api/intelligence/sentinela/briefing";

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
    SentinelaTerritorialService service;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 500L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    @Test
    void analistaFiscalEConsultaRecebem403() throws Exception {
        for (Perfil p : List.of(Perfil.ANALISTA, Perfil.FISCAL, Perfil.CONSULTA)) {
            mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(p))).andExpect(status().isForbidden());
        }
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void semBriefingEGeracaoEmFalhaDa503ComRetryAfter() throws Exception {
        when(service.briefing()).thenThrow(new SentinelaIndisponivelException(42));
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(Perfil.GESTOR)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void adminEGestorRecebemOBriefing() throws Exception {
        BriefingSentinela.Anomalia a = new BriefingSentinela.Anomalia("INPE_SEM_DEMANDA:5107909", "INPE_SEM_DEMANDA",
                "ALTA", "Sinop: 32 registro(s) do INPE sem demanda aberta", "desc", Map.of("total", 32),
                List.of(new BriefingSentinela.AcaoSugerida("CRIAR_DEMANDA_MONITORAMENTO", "Abrir",
                        Map.of("origem", "MONITORAMENTO", "municipioId", 5107909))), "Sinop");
        when(service.briefing()).thenReturn(new BriefingSentinela("AMBIENTE DEMONSTRATIVO", LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(10), "AGENDADA", "DEMONSTRATIVO", null, "Briefing de hoje.", null,
                null, new BriefingSentinela.Totais(1, 1, 0, 0), List.of(a), Map.of(), List.of(), List.of()));
        for (Perfil p : List.of(Perfil.ADMIN, Perfil.GESTOR)) {
            mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(p)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.modo").value("DEMONSTRATIVO"))
                    .andExpect(jsonPath("$.anomalias[0].severidade").value("ALTA"))
                    .andExpect(jsonPath("$.anomalias[0].acoes[0].parametros.origem").value("MONITORAMENTO"));
        }
    }
}
