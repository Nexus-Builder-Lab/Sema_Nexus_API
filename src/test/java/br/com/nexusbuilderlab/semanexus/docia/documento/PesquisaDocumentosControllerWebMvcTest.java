package br.com.nexusbuilderlab.semanexus.docia.documento;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.docia.assistente.BuscaDocumentos;
import br.com.nexusbuilderlab.semanexus.docia.vetorial.Recuperacao;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** {@code GET /api/documentos/busca} (Issue #141): autenticação, validação problem+json e modo de recuperação. */
@WebMvcTest(controllers = PesquisaDocumentosController.class)
@Import({SecurityConfig.class, PesquisaDocumentosService.class, PesquisaDocumentosControllerWebMvcTest.Config.class})
class PesquisaDocumentosControllerWebMvcTest {

    private static final String URL = "/api/documentos/busca";

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
    BuscaDocumentos busca;

    @org.junit.jupiter.api.BeforeEach
    void preparar() {
        when(busca.preparar(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(i -> new BuscaDocumentos.ConsultaPreparada(i.getArgument(0), null));
    }

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 500L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(get(URL).param("q", "queimada")).andExpect(status().isUnauthorized());
    }

    @Test
    void consultaRecebeResultadoComRecuperacaoHibrida() throws Exception {
        Documento d = mock(Documento.class);
        when(d.getId()).thenReturn(4L);
        when(d.getTitulo()).thenReturn("Relatório de Monitoramento de Desmatamento — Colniza");
        when(busca.pesquisar(any(BuscaDocumentos.ConsultaPreparada.class), eq(10), any())).thenReturn(new BuscaDocumentos.Resultado(
                List.of(new BuscaDocumentos.Achado(d, "Foram detectados 4 alertas.", "alta", 0.731)),
                Recuperacao.HIBRIDA));
        mvc.perform(get(URL).param("q", " área desmatada ").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.CONSULTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recuperacao").value("HIBRIDA"))
                .andExpect(jsonPath("$.consulta").value("área desmatada"))
                .andExpect(jsonPath("$.resultados[0].documento.id").value(4))
                .andExpect(jsonPath("$.resultados[0].aderenciaTextual").value("alta"))
                .andExpect(jsonPath("$.resultados[0].similaridadeSemantica").value(0.731));
        verify(busca).preparar("área desmatada", true);
    }

    @Test
    void modoLexicalForcaABuscaTextual() throws Exception {
        when(busca.pesquisar(any(BuscaDocumentos.ConsultaPreparada.class), anyInt(), any()))
                .thenReturn(BuscaDocumentos.Resultado.lexical(List.of()));
        mvc.perform(get(URL).param("q", "queimada").param("modo", "lexical").param("limite", "3")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Perfil.FISCAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recuperacao").value("LEXICAL"))
                .andExpect(jsonPath("$.resultados").isEmpty());
        verify(busca).preparar("queimada", false);
    }

    @Test
    void parametrosInvalidosDevolvem400ProblemJson() throws Exception {
        String token = bearer(Perfil.ANALISTA);
        for (var req : List.of(get(URL), get(URL).param("q", "a"), get(URL).param("q", "x".repeat(201)),
                get(URL).param("q", "queimada").param("limite", "0"),
                get(URL).param("q", "queimada").param("limite", "21"),
                get(URL).param("q", "queimada").param("modo", "VETORIAL"))) {
            mvc.perform(req.header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }
}
