package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.Importacao;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.Ponto;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.PontoDetalhe;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** Detalhe do ponto do INPE no mapa: dados públicos, qualquer perfil autenticado; 401 sem token; 404 inexistente. */
@WebMvcTest(controllers = ReferenciaSateliteController.class)
@Import({SecurityConfig.class, ReferenciaSateliteControllerWebMvcTest.Config.class})
class ReferenciaSateliteControllerWebMvcTest {

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
    ReferenciaSateliteService service;
    @MockitoBean
    AuditService auditService;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 600L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    static PontoDetalhe deter() {
        Ponto p = new Ponto(9L, "DESMATAMENTO", LocalDateTime.parse("2026-10-09T00:00:00"), new BigDecimal("-11.872"),
                new BigDecimal("-55.512"), 5107909, "Sinop", "AMAZONIA", "AMAZONIA-1", "CICATRIZ_DE_QUEIMADA",
                new BigDecimal("0.1320"), new BigDecimal("13.20"));
        Importacao imp = new Importacao(1L, "INPE - TerraBrasilis", "DESMATAMENTO", LocalDate.parse("2026-09-13"),
                LocalDate.parse("2026-10-29"), LocalDateTime.parse("2026-11-24T10:00:00"), 1538, "https://x",
                "CC BY-SA 4.0", "deter_mt.geojson.gz");
        Map<String, Object> geometria = Map.of("type", "MultiPolygon", "coordinates",
                List.of(List.of(List.of(List.of(-55.52, -11.88), List.of(-55.5, -11.88), List.of(-55.5, -11.86),
                        List.of(-55.52, -11.88)))));
        return new PontoDetalhe(ReferenciaDto.FONTE, false, ReferenciaDto.AVISO, p, true, "areamunkm",
                new BigDecimal("0.13200473633075194"), "Área do polígono do alerta informada pelo INPE/TerraBrasilis", geometria,
                Map.of("gid", "40570_curr"), imp);
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get("/api/intelligence/referencia/pontos/9")).andExpect(status().isUnauthorized());
        verify(service, never()).detalhe(9L);
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void qualquerPerfilAutenticadoVeODetalheComAreaEPoligono(Perfil perfil) throws Exception {
        when(service.detalhe(9L)).thenReturn(deter());
        mvc.perform(get("/api/intelligence/referencia/pontos/9").header(HttpHeaders.AUTHORIZATION, bearer(perfil)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte").value("INPE — dados públicos"))
                .andExpect(jsonPath("$.oficialSema").value(false))
                .andExpect(jsonPath("$.ponto.areaKm2").value(0.132))
                .andExpect(jsonPath("$.ponto.areaHa").value(13.2))
                .andExpect(jsonPath("$.areaCampoFonte").value("areamunkm"))
                .andExpect(jsonPath("$.geometria.type").value("MultiPolygon"))
                .andExpect(jsonPath("$.importacao.arquivo").value("deter_mt.geojson.gz"));
    }

    @Test
    void pontoInexistenteRetorna404ProblemJson() throws Exception {
        when(service.detalhe(77L)).thenThrow(RecursoNaoEncontradoException.de("Ponto de satélite", 77L));
        mvc.perform(get("/api/intelligence/referencia/pontos/77")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Perfil.CONSULTA)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void idInvalidoRetorna400() throws Exception {
        mvc.perform(get("/api/intelligence/referencia/pontos/abc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Perfil.CONSULTA)))
                .andExpect(status().isBadRequest());
    }
}
