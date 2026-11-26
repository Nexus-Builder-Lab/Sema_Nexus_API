package br.com.nexusbuilderlab.semanexus.agentes.governanca;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import br.com.nexusbuilderlab.semanexus.docia.assistente.CasosAvaliacaoDocia;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import br.com.nexusbuilderlab.semanexus.flux.agente.CasosAvaliacaoFlux;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DistribuicaoRepository;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Issue #131: bateria só para ADMIN, todos os casos aprovados e nenhuma leitura/gravação operacional (repositórios e
 * JDBC sem interação); feedback do DocIA para qualquer autenticado.
 */
@WebMvcTest(controllers = GovernancaIaController.class)
@Import({SecurityConfig.class, GovernancaIaControllerWebMvcTest.Config.class, BateriaAvaliacaoService.class,
        CasosAvaliacaoFlux.Triagem.class, CasosAvaliacaoFlux.Distribuicao.class, CasosAvaliacaoDocia.class})
class GovernancaIaControllerWebMvcTest {

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService("segredo-de-teste-com-mais-de-32-caracteres!!", Duration.ofHours(8), Clock.systemUTC());
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
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
    GovernancaIaService governanca;
    @MockitoBean
    JdbcTemplate jdbc;
    @MockitoBean
    DemandaRepository demandas;
    @MockitoBean
    DistribuicaoRepository distribuicoes;
    @MockitoBean
    DocumentoRepository documentos;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 400L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    @Test
    void bateriaSoParaAdmin() throws Exception {
        for (Perfil p : List.of(Perfil.GESTOR, Perfil.FISCAL, Perfil.ANALISTA, Perfil.CONSULTA)) {
            mvc.perform(post("/api/sistema/agentes/avaliar").header(HttpHeaders.AUTHORIZATION, bearer(p)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/sistema/agentes/avaliar")).andExpect(status().isUnauthorized());
    }

    @Test
    void bateriaAprovaTodosOsCasosSemDadosOperacionais() throws Exception {
        mvc.perform(post("/api/sistema/agentes/avaliar").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("DETERMINISTICO"))
                .andExpect(jsonPath("$.reprovados").value(0))
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(20)))
                .andExpect(jsonPath("$.casos[?(@.tipo == 'ADVERSARIAL')]").isNotEmpty())
                .andExpect(jsonPath("$.casos[?(@.agente == 'triagem')]").isNotEmpty())
                .andExpect(jsonPath("$.casos[?(@.agente == 'distribuicao')]").isNotEmpty())
                .andExpect(jsonPath("$.casos[?(@.agente == 'docia')]").isNotEmpty());
        verifyNoInteractions(jdbc, demandas, distribuicoes, documentos, governanca);
        verify(auditService).registrar(eq("AGENTES_AVALIAR"), eq("AGENTE"), eq("bateria"), anyString());
    }

    @Test
    void feedbackDoDociaParaQualquerAutenticado() throws Exception {
        String id = "3f1c2b7e-8a3d-4a51-9d0e-2b6f9c1e7a10";
        mvc.perform(post("/api/docia/respostas/" + id + "/avaliacao")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Perfil.CONSULTA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avaliacao\":\"UTIL\"}"))
                .andExpect(status().isNoContent());
        verify(governanca).avaliarResposta(any(), eq(id), eq(AvaliacaoRespostaRequest.Avaliacao.UTIL));
        mvc.perform(post("/api/docia/respostas/" + id + "/avaliacao")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avaliacao\":\"UTIL\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void feedbackInvalidoOuDeOutroUsuario() throws Exception {
        mvc.perform(post("/api/docia/respostas/x/avaliacao")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Perfil.FISCAL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avaliacao\":\"OTIMA\"}"))
                .andExpect(status().isBadRequest());
        doThrow(new RecursoNaoEncontradoException("Resposta do assistente não encontrada."))
                .when(governanca).avaliarResposta(any(), eq("outra"), any());
        mvc.perform(post("/api/docia/respostas/outra/avaliacao")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Perfil.FISCAL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avaliacao\":\"IMPRECISA\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
