package br.com.nexusbuilderlab.semanexus.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CatalogoController;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CatalogoService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.dto.ItemCatalogo;
import br.com.nexusbuilderlab.semanexus.core.catalogo.dto.MunicipioDto;
import br.com.nexusbuilderlab.semanexus.core.dashboard.DashboardController;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeController;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeService;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.EquipeDto;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.MembroDto;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.setor.SetorController;
import br.com.nexusbuilderlab.semanexus.core.setor.SetorService;
import br.com.nexusbuilderlab.semanexus.core.setor.dto.SetorDto;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.DemandaDocumentoController;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoController;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaController;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaFiltro;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import br.com.nexusbuilderlab.semanexus.flux.demanda.EquipeDemandasController;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.IndicadorController;
import br.com.nexusbuilderlab.semanexus.intelligence.mapa.MapaController;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
/**
 * Fatia web dos endpoints da Issue #7: catálogos e estrutura (implementados, com autorização por perfil) e
 * contratos ainda pendentes (501 em problem+json, depois de autenticação, autorização e validação).
 */
@WebMvcTest(controllers = {
    CatalogoController.class, SetorController.class, EquipeController.class,
    DemandaController.class, EquipeDemandasController.class, DocumentoController.class, DemandaDocumentoController.class,
    IndicadorController.class, MapaController.class, DashboardController.class})
@Import({SecurityConfig.class, EndpointsContratoWebMvcTest.Config.class})
class EndpointsContratoWebMvcTest {

    static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres!!";
    static final String PROBLEM_JSON = "application/problem+json";

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService(SEGREDO, Duration.ofHours(8), Clock.systemUTC());
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;

    @MockitoBean
    UsuarioRepository usuarios;
    @MockitoBean
    CatalogoService catalogos;
    @MockitoBean
    SetorService setores;
    @MockitoBean
    EquipeService equipes;
    @MockitoBean
    DemandaService demandas;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 100L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    private ResultActionsHelper como(Perfil perfil) {
        return new ResultActionsHelper(bearer(perfil));
    }

    /** Pequeno auxiliar para repetir o cabeçalho Authorization. */
    private final class ResultActionsHelper {
        private final String token;

        ResultActionsHelper(String token) {
            this.token = token;
        }

        org.springframework.test.web.servlet.ResultActions faz(MockHttpServletRequestBuilder req) throws Exception {
            return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, token));
        }
    }

    private static String demandaValida() {
        return "{\"titulo\":\"Título de teste\",\"descricao\":\"Descrição fictícia\",\"origem\":\"DENUNCIA\","
                + "\"municipioId\":5103403,\"dataRecebimento\":\"" + LocalDate.now().minusDays(1) + "\"}";
    }

    private static MockHttpServletRequestBuilder uploadValido() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "laudo.txt", "text/plain", "conteudo ficticio".getBytes());
        return multipart("/api/documentos").file(arquivo)
                .param("titulo", "Laudo de teste").param("categoria", "Relatório técnico").param("tipo", "Laudo")
                .param("dataDocumento", LocalDate.now().minusDays(2).toString())
                .param("palavrasChave", "desmatamento", "vistoria");
    }

    // ------------------------------------------------------------------ catálogos (implementados)

    @Test
    void catalogosExigemAutenticacao() throws Exception {
        for (String caminho : List.of("municipios", "biomas", "categorias", "origens", "prioridades", "status")) {
            mvc.perform(get("/api/catalogos/" + caminho))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        }
    }

    @Test
    void qualquerPerfilConsultaOsCatalogos() throws Exception {
        when(catalogos.status()).thenReturn(List.of(new ItemCatalogo("PENDENTE", "Pendente", 1)));
        for (Perfil perfil : Perfil.values()) {
            como(perfil).faz(get("/api/catalogos/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].codigo").value("PENDENTE"))
                    .andExpect(jsonPath("$[0].rotulo").value("Pendente"))
                    .andExpect(jsonPath("$[0].ordem").value(1));
        }
    }

    @Test
    void municipiosRetornaListaEAceitaFiltroDeBioma() throws Exception {
        var cuiaba = new MunicipioDto(5103403, "Cuiabá", "Baixada Cuiabana", Bioma.CERRADO,
                new BigDecimal("-15.601"), new BigDecimal("-56.097"));
        when(catalogos.municipios(Bioma.CERRADO)).thenReturn(List.of(cuiaba));

        como(Perfil.CONSULTA).faz(get("/api/catalogos/municipios").param("bioma", "CERRADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(5103403))
                .andExpect(jsonPath("$[0].nome").value("Cuiabá"))
                .andExpect(jsonPath("$[0].bioma").value("CERRADO"))
                .andExpect(jsonPath("$[0].latitude").value(-15.601));
    }

    @Test
    void biomaInvalidoRetorna400ProblemJson() throws Exception {
        como(Perfil.ADMIN).faz(get("/api/catalogos/municipios").param("bioma", "DESERTO"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    // ------------------------------------------------------------------ estrutura (implementada)

    @Test
    void setoresEEquipesSoParaAdminEGestor() throws Exception {
        when(setores.listar(null)).thenReturn(List.of(new SetorDto(1L, "DEMO-FISC", "Setor demo", true)));
        when(equipes.listar(null, null)).thenReturn(List.of());
        when(equipes.membros(1L)).thenReturn(List.of());

        for (Perfil permitido : List.of(Perfil.ADMIN, Perfil.GESTOR)) {
            como(permitido).faz(get("/api/setores")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].sigla").value("DEMO-FISC"));
            como(permitido).faz(get("/api/equipes")).andExpect(status().isOk());
            como(permitido).faz(get("/api/equipes/1/membros")).andExpect(status().isOk());
        }
        for (Perfil negado : List.of(Perfil.FISCAL, Perfil.ANALISTA, Perfil.CONSULTA)) {
            for (String caminho : List.of("/api/setores", "/api/equipes", "/api/equipes/1/membros")) {
                como(negado).faz(get(caminho))
                        .andExpect(status().isForbidden())
                        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
            }
        }
    }

    @Test
    void estruturaSemTokenRetorna401() throws Exception {
        mvc.perform(get("/api/setores")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/equipes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/equipes/1/membros")).andExpect(status().isUnauthorized());
    }

    @Test
    void equipesRepassaFiltros() throws Exception {
        when(equipes.listar(2L, true)).thenReturn(List.of(
                new EquipeDto(5L, "Equipe Alfa", 2L, "DEMO-FISC", null, 3, true)));

        como(Perfil.GESTOR).faz(get("/api/equipes").param("setorId", "2").param("ativo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].setorSigla").value("DEMO-FISC"))
                .andExpect(jsonPath("$[0].totalMembros").value(3))
                .andExpect(jsonPath("$[0].responsavel").doesNotExist());
    }

    @Test
    void membrosNaoExpoemSenhaEEquipeInexistenteRetorna404() throws Exception {
        when(equipes.membros(1L)).thenReturn(List.of(
                new MembroDto(3L, "Fiscal Um", "fiscal1@exemplo.test", Perfil.FISCAL, true)));
        when(equipes.membros(404L)).thenThrow(RecursoNaoEncontradoException.de("Equipe", 404L));

        como(Perfil.ADMIN).faz(get("/api/equipes/1/membros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].perfil").value("FISCAL"))
                .andExpect(jsonPath("$[0].senhaHash").doesNotExist())
                .andExpect(jsonPath("$[0].senha").doesNotExist());
        como(Perfil.ADMIN).faz(get("/api/equipes/404/membros"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ------------------------------------------------------------------ contratos pendentes (501)

    @Test
    void endpointPendenteRespondeNaoImplementadoComAIssue() throws Exception {
        como(Perfil.ANALISTA).faz(post("/api/demandas/1/triagem/iniciar"))
                .andExpect(status().isNotImplemented())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.title").value("Endpoint ainda não implementado"))
                .andExpect(jsonPath("$.type").value("https://semanexus.demo/problemas/nao-implementado"))
                .andExpect(jsonPath("$.issue").value(12))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Issue #12")));
    }

    @Test
    void endpointsPendentesExigemAutenticacao() throws Exception {
        mvc.perform(get("/api/demandas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/documentos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/intelligence/mapa")).andExpect(status().isUnauthorized());
    }

    @Test
    void autorizacaoPorPerfilVemAntesDo501() throws Exception {
        // CONSULTA não cadastra, não tria, não distribui, não registra fiscalização, não envia documento.
        como(Perfil.CONSULTA).faz(post("/api/demandas").contentType(MediaType.APPLICATION_JSON).content(demandaValida()))
                .andExpect(status().isForbidden());
        como(Perfil.CONSULTA).faz(post("/api/demandas/1/triagem/iniciar")).andExpect(status().isForbidden());
        como(Perfil.CONSULTA).faz(post("/api/demandas/1/inicio")).andExpect(status().isForbidden());
        como(Perfil.CONSULTA).faz(delete("/api/demandas/1/documentos/2")).andExpect(status().isForbidden());
        // FISCAL não cadastra, não distribui e não conclui.
        como(Perfil.FISCAL).faz(post("/api/demandas").contentType(MediaType.APPLICATION_JSON).content(demandaValida()))
                .andExpect(status().isForbidden());
        como(Perfil.FISCAL).faz(post("/api/demandas/1/distribuicao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipeId\":1,\"fiscalId\":2,\"prazo\":\"" + LocalDate.now().plusDays(5) + "\"}"))
                .andExpect(status().isForbidden());
        como(Perfil.FISCAL).faz(post("/api/demandas/1/conclusao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"resultadoFinal\":\"ok\",\"justificativa\":\"ok\"}")).andExpect(status().isForbidden());
        // ANALISTA não distribui nem acompanha.
        como(Perfil.ANALISTA).faz(post("/api/demandas/1/distribuicao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipeId\":1,\"fiscalId\":2,\"prazo\":\"" + LocalDate.now().plusDays(5) + "\"}"))
                .andExpect(status().isForbidden());
        como(Perfil.ANALISTA).faz(post("/api/demandas/1/inicio")).andExpect(status().isForbidden());
        // Só ADMIN exclui demanda.
        como(Perfil.GESTOR).faz(delete("/api/demandas/1")).andExpect(status().isForbidden());
        como(Perfil.ADMIN).faz(delete("/api/demandas/1")).andExpect(status().isNoContent());
    }

    @Test
    void perfisComPermissaoChegamAo501() throws Exception {
        como(Perfil.FISCAL).faz(post("/api/demandas/1/inicio")).andExpect(status().isNotImplemented());
        como(Perfil.FISCAL).faz(post("/api/demandas/1/atividades").contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Vistoria agendada\"}")).andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.issue").value(13));
        como(Perfil.GESTOR).faz(post("/api/demandas/1/distribuicao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipeId\":1,\"fiscalId\":2,\"prazo\":\"" + LocalDate.now().plusDays(5) + "\"}"))
                .andExpect(status().isNotImplemented()).andExpect(jsonPath("$.issue").value(12));
        como(Perfil.CONSULTA).faz(get("/api/demandas/1/historico")).andExpect(status().isOk());
        como(Perfil.GESTOR).faz(get("/api/equipes/1/demandas")).andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.issue").value(12));
    }

    @Test
    void validacaoVemAntesDo501ComErrosPorCampo() throws Exception {
        como(Perfil.ANALISTA).faz(post("/api/demandas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"abc\",\"origem\":\"DENUNCIA\",\"municipioId\":5103403,"
                                + "\"latitude\":10.0,\"dataRecebimento\":\"" + LocalDate.now().plusDays(3) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[?(@.campo=='titulo')]").exists())
                .andExpect(jsonPath("$.errors[?(@.campo=='descricao')]").exists())
                .andExpect(jsonPath("$.errors[?(@.campo=='latitude')]").exists())
                .andExpect(jsonPath("$.errors[?(@.campo=='dataRecebimento')]").exists());
    }

    @Test
    void triagemExigeDecisaoEJustificativa() throws Exception {
        como(Perfil.GESTOR).faz(post("/api/demandas/1/triagem").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.campo=='decisao')]").exists())
                .andExpect(jsonPath("$.errors[?(@.campo=='justificativa')]").exists());
    }

    @Test
    void distribuicaoComPrazoNoPassadoRetorna400() throws Exception {
        como(Perfil.GESTOR).faz(post("/api/demandas/1/distribuicao").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipeId\":1,\"fiscalId\":2,\"prazo\":\"" + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.campo=='prazo')]").exists());
    }

    @Test
    void listagemDeDemandasAceitaFiltrosEPaginacaoMasRejeitaValorInvalido() throws Exception {
        como(Perfil.CONSULTA).faz(get("/api/demandas")
                        .param("status", "EM_ANDAMENTO").param("municipioId", "5103403").param("atrasada", "true")
                        .param("de", "2026-01-01").param("ate", "2026-12-31").param("q", "DEM-2026")
                        .param("page", "0").param("size", "10").param("sort", "prazo,asc"))
                .andExpect(status().isOk());
        ArgumentCaptor<DemandaFiltro> filtro = ArgumentCaptor.forClass(DemandaFiltro.class);
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(demandas).listar(any(), filtro.capture(), pagina.capture());
        assertThat(filtro.getValue().status()).isEqualTo(StatusDemanda.EM_ANDAMENTO);
        assertThat(filtro.getValue().municipioId()).isEqualTo(5103403);
        assertThat(filtro.getValue().atrasada()).isTrue();
        assertThat(filtro.getValue().de()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(filtro.getValue().ate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(filtro.getValue().q()).isEqualTo("DEM-2026");
        assertThat(pagina.getValue().getPageSize()).isEqualTo(10);
        assertThat(pagina.getValue().getSort().getOrderFor("prazo")).isNotNull();

        como(Perfil.CONSULTA).faz(get("/api/demandas").param("status", "ATRASADA"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        como(Perfil.CONSULTA).faz(get("/api/demandas").param("de", "10/11/2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadMultipartExigeArquivoEPerfilComEscrita() throws Exception {
        como(Perfil.ANALISTA).faz(uploadValido())
                .andExpect(status().isNotImplemented()).andExpect(jsonPath("$.issue").value(15));
        como(Perfil.CONSULTA).faz(uploadValido()).andExpect(status().isForbidden());
        // Sem a parte "arquivo" e sem campos obrigatórios: 400 com erros por campo.
        como(Perfil.ANALISTA).faz(multipart("/api/documentos").param("titulo", "ab"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[?(@.campo=='arquivo')]").exists())
                .andExpect(jsonPath("$.errors[?(@.campo=='titulo')]").exists())
                .andExpect(jsonPath("$.errors[?(@.campo=='dataDocumento')]").exists());
    }

    @Test
    void intelligenceDashboardEMapaAceitamFiltrosERespondem501() throws Exception {
        for (String caminho : List.of("resumo", "serie-mensal", "por-municipio", "por-bioma", "por-categoria", "por-situacao", "mapa")) {
            como(Perfil.CONSULTA).faz(get("/api/intelligence/" + caminho)
                            .param("bioma", "AMAZONIA").param("categoria", "QUEIMADA_INCENDIO").param("de", "2026-01-01"))
                    .andExpect(status().isNotImplemented()).andExpect(jsonPath("$.issue").value(17));
        }
        como(Perfil.FISCAL).faz(get("/api/dashboard")).andExpect(status().isNotImplemented());
        como(Perfil.CONSULTA).faz(get("/api/intelligence/resumo").param("bioma", "DESERTO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vinculoDeDocumentoExigeCorpoEPerfilComEscrita() throws Exception {
        como(Perfil.CONSULTA).faz(get("/api/demandas/1/documentos")).andExpect(status().isNotImplemented());
        como(Perfil.CONSULTA).faz(post("/api/demandas/1/documentos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentoId\":7}")).andExpect(status().isForbidden());
        como(Perfil.ANALISTA).faz(post("/api/demandas/1/documentos").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("documentoId"));
        como(Perfil.ANALISTA).faz(post("/api/demandas/1/documentos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentoId\":7}")).andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.issue").value(16));
        como(Perfil.FISCAL).faz(delete("/api/demandas/1/documentos/7")).andExpect(status().isNotImplemented());
    }

    @Test
    void downloadEDetalhesDeDocumentoExigemAutenticacaoEChegamAo501() throws Exception {
        mvc.perform(get("/api/documentos/7/arquivo")).andExpect(status().isUnauthorized());
        como(Perfil.CONSULTA).faz(get("/api/documentos/7/arquivo")).andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.issue").value(15));
        como(Perfil.CONSULTA).faz(get("/api/documentos/7")).andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.issue").value(16));
        como(Perfil.CONSULTA).faz(get("/api/documentos").param("q", "queimada").param("demandaId", "1"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void cadastroValidoResponde201ComLocationENumero() throws Exception {
        var criada = new br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe(7L, "DEM-2026/0007",
                "Título de teste", "Descrição fictícia", null, null, null, null, null, null, LocalDate.now(), null,
                null, null, StatusDemanda.PENDENTE, false, null, null, null, null, null, null, null, 0, null, null,
                null);
        when(demandas.cadastrar(any(), any())).thenReturn(criada);
        como(Perfil.ANALISTA).faz(post("/api/demandas").contentType(MediaType.APPLICATION_JSON).content(demandaValida()))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Location", org.hamcrest.Matchers.endsWith("/api/demandas/7")))
                .andExpect(jsonPath("$.numero").value("DEM-2026/0007"))
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    void edicaoDeDemandaUsaOMesmoContratoDoCadastro() throws Exception {
        como(Perfil.GESTOR).faz(put("/api/demandas/1").contentType(MediaType.APPLICATION_JSON).content(demandaValida()))
                .andExpect(status().isOk());
        verify(demandas).editar(any(), eq(1L), any());
        como(Perfil.FISCAL).faz(put("/api/demandas/1").contentType(MediaType.APPLICATION_JSON).content(demandaValida()))
                .andExpect(status().isForbidden());
    }
}
