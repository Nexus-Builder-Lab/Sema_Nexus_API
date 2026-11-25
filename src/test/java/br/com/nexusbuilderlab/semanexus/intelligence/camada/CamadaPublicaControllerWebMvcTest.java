package br.com.nexusbuilderlab.semanexus.intelligence.camada;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.FeatureCollection;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.FonteResumo;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.GeoJsonResponse;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.Importacao;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.Metadados;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.ResumoSobreposicao;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.SobreposicaoCamadasPublicas;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CamadaPublicaController.class)
@Import({SecurityConfig.class, CamadaPublicaControllerWebMvcTest.Config.class})
class CamadaPublicaControllerWebMvcTest {

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
    CamadaPublicaService service;
    @MockitoBean
    DemandaService demandas;
    @MockitoBean
    AuditService auditService;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 700L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    @Test
    void semTokenRetorna401ProblemJson() throws Exception {
        mvc.perform(get("/api/intelligence/referencia/camadas/metadados"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/intelligence/referencia/camadas/geojson?camada=UNIDADE_CONSERVACAO"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/intelligence/referencia/camadas/sobreposicao?latitude=-15.6&longitude=-56.1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verify(service, never()).metadados();
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void qualquerPerfilAutenticadoConsultaMetadadosGeojsonESobreposicao(Perfil perfil) throws Exception {
        String auth = bearer(perfil);
        when(service.metadados()).thenReturn(new Metadados(false, LocalDateTime.of(2026, 11, 25, 10, 0),
                CamadaPublicaDto.AVISO, List.of(new Importacao(1L, TipoCamadaPublica.UNIDADE_CONSERVACAO, "MMA/CNUC",
                        LocalDate.of(2025, 8, 1), LocalDateTime.of(2026, 11, 25, 10, 0), 98, "https://dados.mma.gov.br",
                        "CC BY", "ucs_mt.geojson.gz"))));
        when(service.geojson(TipoCamadaPublica.UNIDADE_CONSERVACAO, null, null, null, null))
                .thenReturn(new GeoJsonResponse(TipoCamadaPublica.UNIDADE_CONSERVACAO, "MMA/CNUC", "CC BY",
                        LocalDate.of(2025, 8, 1), LocalDateTime.of(2026, 11, 25, 10, 0), false,
                        CamadaPublicaDto.AVISO, 0, false, new FeatureCollection("FeatureCollection", List.of())));
        when(service.sobreposicaoPonto(-15.6, -56.1, null))
                .thenReturn(new SobreposicaoCamadasPublicas(false, CamadaPublicaDto.AVISO, "COORDENADA", -15.6, -56.1,
                        10.0, List.of(), List.of(), List.of(),
                        new ResumoSobreposicao(false, null, false, null, null, 0, 0, 0,
                                List.of(new FonteResumo(TipoCamadaPublica.UNIDADE_CONSERVACAO, "MMA/CNUC", "CC BY",
                                        LocalDate.of(2025, 8, 1))))));

        mvc.perform(get("/api/intelligence/referencia/camadas/metadados").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oficialSema").value(false))
                .andExpect(jsonPath("$.importacoes[0].licenca").value("CC BY"));

        mvc.perform(get("/api/intelligence/referencia/camadas/geojson?camada=UNIDADE_CONSERVACAO")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oficialSema").value(false))
                .andExpect(jsonPath("$.colecao.type").value("FeatureCollection"));

        mvc.perform(get("/api/intelligence/referencia/camadas/sobreposicao?latitude=-15.6&longitude=-56.1")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oficialSema").value(false))
                .andExpect(jsonPath("$.resumo.fontes[0].licenca").value("CC BY"));
    }

    @Test
    void sobreposicaoPorDemandaRespeitaEscopo403E404ProblemJson() throws Exception {
        when(demandas.detalhar(any(), eq(99L))).thenThrow(new AccessDeniedException("Demanda fora do escopo do fiscal."));
        mvc.perform(get("/api/intelligence/referencia/camadas/sobreposicao?demandaId=99")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Perfil.FISCAL)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        when(demandas.detalhar(any(), eq(404L))).thenThrow(RecursoNaoEncontradoException.de("Demanda", 404L));
        mvc.perform(get("/api/intelligence/referencia/camadas/sobreposicao?demandaId=404")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Perfil.ANALISTA)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        DemandaDetalhe det = org.mockito.Mockito.mock(DemandaDetalhe.class);
        when(demandas.detalhar(any(), eq(10L))).thenReturn(det);
        when(service.sobreposicao(det, 5.0)).thenReturn(new SobreposicaoCamadasPublicas(false,
                CamadaPublicaDto.AVISO, "COORDENADA", -15.6, -56.1, 5.0, List.of(), List.of(), List.of(),
                new ResumoSobreposicao(false, null, false, null, null, 0, 0, 0, List.of())));
        mvc.perform(get("/api/intelligence/referencia/camadas/sobreposicao?demandaId=10&raioKm=5")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Perfil.FISCAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oficialSema").value(false));
    }

    @Test
    void parametrosInvalidosRetornam400ProblemJson() throws Exception {
        String auth = bearer(Perfil.ANALISTA);
        mvc.perform(get("/api/intelligence/referencia/camadas/geojson?camada=INEXISTENTE")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/intelligence/referencia/camadas/sobreposicao")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/intelligence/referencia/camadas/sobreposicao?latitude=-15.6")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/intelligence/referencia/camadas/sobreposicao?latitude=-15.6&longitude=-56.1&raioKm=120")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/intelligence/referencia/camadas/sobreposicao?latitude=-15.6&longitude=-56.1&demandaId=1")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
