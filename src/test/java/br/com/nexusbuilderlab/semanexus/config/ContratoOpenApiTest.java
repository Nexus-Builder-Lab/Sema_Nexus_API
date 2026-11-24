package br.com.nexusbuilderlab.semanexus.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Garante que {@code /v3/api-docs} (o {@code openapi.json} que o CI publica para os mocks do Web) descreve
 * todos os endpoints P0, com schemas e respostas de erro. Roda com o profile {@code openapi}, sem banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("openapi")
class ContratoOpenApiTest {

    /** Endpoints P0 que o contrato precisa conter: "MÉTODO caminho". */
    static final List<String> ENDPOINTS_P0 = List.of(
            // Core (implementados)
            "POST /api/auth/login", "POST /api/auth/logout", "GET /api/auth/me", "GET /api/auth/config",
            "GET /api/catalogos/municipios", "GET /api/catalogos/biomas", "GET /api/catalogos/categorias",
            "GET /api/catalogos/origens", "GET /api/catalogos/prioridades", "GET /api/catalogos/status",
            "GET /api/setores", "GET /api/equipes", "GET /api/equipes/{id}/membros",
            "GET /api/notificacoes", "GET /api/notificacoes/nao-lidas/contagem", "PATCH /api/notificacoes/{id}/lida",
            // Flux (contrato)
            "POST /api/demandas", "PUT /api/demandas/{id}", "GET /api/demandas", "GET /api/demandas/{id}",
            "DELETE /api/demandas/{id}", "GET /api/demandas/{id}/historico",
            "POST /api/demandas/{id}/triagem/iniciar", "POST /api/demandas/{id}/triagem",
            "POST /api/demandas/{id}/distribuicao", "GET /api/equipes/{id}/demandas",
            "POST /api/demandas/{id}/inicio", "POST /api/demandas/{id}/atividades",
            "POST /api/demandas/{id}/fiscalizacao", "POST /api/demandas/{id}/conclusao",
            "POST /api/demandas/{id}/devolucao",
            "GET /api/demandas/{id}/documentos", "POST /api/demandas/{id}/documentos",
            "DELETE /api/demandas/{id}/documentos/{documentoId}",
            "GET /api/flux/prazos", "GET /api/flux/prazos/resumo", "GET /api/flux/produtividade",
            // DocIA (contrato)
            "POST /api/documentos", "POST /api/documentos/pre-analise", "GET /api/documentos", "GET /api/documentos/{id}", "GET /api/documentos/{id}/arquivo",
            "POST /api/docia/perguntas",
            // Agentes de IA (Issue #106)
            "GET /api/agentes/status", "POST /api/demandas/{id}/triagem/sugestao", "GET /api/sistema/painel",
            "POST /api/documentos/comparacao",
            // Intelligence e dashboard (contrato)
            "GET /api/intelligence/resumo", "GET /api/intelligence/serie-mensal", "GET /api/intelligence/por-municipio",
            "GET /api/intelligence/por-bioma", "GET /api/intelligence/por-categoria", "GET /api/intelligence/por-situacao",
            "GET /api/intelligence/mapa", "GET /api/dashboard",
            "GET /api/relatorios/demandas", "GET /api/relatorios/demandas.csv",
            "GET /api/intelligence/referencia/metadados", "GET /api/intelligence/referencia/pontos",
            "GET /api/intelligence/referencia/por-municipio", "GET /api/intelligence/referencia/pontos/{id}",
            // Core — auditoria (implementado)
            "GET /api/auditoria",
            // Core — gestão de usuários, setores e equipes (Issue #24)
            "GET /api/usuarios", "POST /api/usuarios", "GET /api/usuarios/{id}", "PUT /api/usuarios/{id}",
            "PATCH /api/usuarios/{id}/ativacao", "POST /api/setores", "PUT /api/setores/{id}",
            "POST /api/equipes", "PUT /api/equipes/{id}", "PUT /api/equipes/{id}/membros");

    /** Endpoints com contrato publicado que ainda respondem 501 (saem desta lista quando a Issue os implementa). */
    static final Map<String, Integer> PENDENTES = Map.of();

    static JsonNode contrato;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    private JsonNode contrato() throws Exception {
        if (contrato == null) {
            String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            contrato = mapper.readTree(json);
            // Cópia para inspeção manual (target/ é ignorado pelo Git).
            Path destino = Path.of("target", "openapi-contrato-teste.json");
            Files.createDirectories(destino.getParent());
            Files.writeString(destino, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(contrato));
        }
        return contrato;
    }

    private JsonNode operacao(String chave) throws Exception {
        String[] partes = chave.split(" ", 2);
        JsonNode op = contrato().path("paths").path(partes[1]).path(partes[0].toLowerCase());
        assertThat(op.isMissingNode()).as("operação %s no contrato", chave).isFalse();
        return op;
    }

    @BeforeAll
    static void limpar() {
        contrato = null;
    }

    @Test
    void contratoContemTodosOsEndpointsP0() throws Exception {
        List<String> ausentes = new ArrayList<>();
        for (String chave : ENDPOINTS_P0) {
            String[] partes = chave.split(" ", 2);
            if (contrato().path("paths").path(partes[1]).path(partes[0].toLowerCase()).isMissingNode()) {
                ausentes.add(chave);
            }
        }
        assertThat(ausentes).as("endpoints P0 ausentes do /v3/api-docs").isEmpty();
    }

    @Test
    void naoHaEndpointNaApiForaDoContratoDocumentado() throws Exception {
        List<String> existentes = new ArrayList<>();
        contrato().path("paths").fields().forEachRemaining(caminho -> caminho.getValue().fieldNames()
                .forEachRemaining(metodo -> existentes.add(metodo.toUpperCase() + " " + caminho.getKey())));
        assertThat(existentes).containsExactlyInAnyOrderElementsOf(ENDPOINTS_P0);
    }

    @Test
    void operacoesPendentesDocumentam501EAvisam() throws Exception {
        for (var pendente : PENDENTES.entrySet()) {
            JsonNode op = operacao(pendente.getKey());
            assertThat(op.path("responses").has("501")).as("501 em %s", pendente.getKey()).isTrue();
            assertThat(op.path("x-implementado").asBoolean(true)).as("x-implementado em %s", pendente.getKey()).isFalse();
            assertThat(op.path("x-issue").asInt()).as("x-issue em %s", pendente.getKey()).isEqualTo(pendente.getValue());
            assertThat(op.path("description").asText()).contains("Issue #" + pendente.getValue());
        }
    }

    @Test
    void operacoesImplementadasNaoDeclaram501() throws Exception {
        for (String chave : ENDPOINTS_P0) {
            if (!PENDENTES.containsKey(chave)) {
                assertThat(operacao(chave).path("responses").has("501")).as("501 em %s", chave).isFalse();
            }
        }
    }

    @Test
    void errosPadraoUsamProblemJsonComSchemaProblema() throws Exception {
        for (String chave : ENDPOINTS_P0) {
            JsonNode respostas = operacao(chave).path("responses");
            respostas.fields().forEachRemaining(r -> {
                int codigo = Integer.parseInt(r.getKey());
                if (codigo >= 400) {
                    JsonNode problema = r.getValue().path("content").path("application/problem+json").path("schema");
                    assertThat(problema.path("$ref").asText())
                            .as("%s resposta %s", chave, r.getKey()).isEqualTo("#/components/schemas/Problema");
                }
            });
        }
        JsonNode schemas = contrato().path("components").path("schemas");
        assertThat(schemas.has("Problema")).isTrue();
        assertThat(schemas.path("Problema").path("properties").has("errors")).isTrue();
        assertThat(schemas.has("ErroCampo")).isTrue();
    }

    @Test
    void todaOperacaoDeclaraRespostaDeSucesso2xx() throws Exception {
        List<String> sem2xx = new ArrayList<>();
        for (String chave : ENDPOINTS_P0) {
            boolean tem2xx = false;
            var codigos = operacao(chave).path("responses").fieldNames();
            while (codigos.hasNext()) {
                tem2xx |= codigos.next().startsWith("2");
            }
            if (!tem2xx) {
                sem2xx.add(chave);
            }
        }
        assertThat(sem2xx).as("operações sem resposta 2xx").isEmpty();
        JsonNode login = operacao("POST /api/auth/login").path("responses").path("200");
        assertThat(login.path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/LoginResponse");
        assertThat(contrato().path("components").path("schemas").path("LoginResponse").path("properties").fieldNames())
                .toIterable().contains("token", "expiresIn", "usuario");
    }

    @Test
    void erro401AusenteSoNosEndpointsPublicos() throws Exception {
        List<String> publicos = List.of("POST /api/auth/login", "GET /api/auth/config");
        for (String chave : ENDPOINTS_P0) {
            boolean tem401 = operacao(chave).path("responses").has("401");
            if (publicos.contains(chave)) {
                // O login documenta 401 de credenciais inválidas; a configuração pública não tem 401.
                assertThat(tem401).as("401 em %s", chave).isEqualTo(chave.equals("POST /api/auth/login"));
            } else {
                assertThat(tem401).as("401 em %s", chave).isTrue();
            }
        }
    }

    @Test
    void erros400403404409ConformeAOperacao() throws Exception {
        JsonNode cadastro = operacao("POST /api/demandas").path("responses");
        assertThat(cadastro.has("201")).isTrue();
        assertThat(cadastro.has("400")).isTrue();
        assertThat(cadastro.has("403")).isTrue();
        assertThat(cadastro.has("404")).isFalse();

        JsonNode distribuicao = operacao("POST /api/demandas/{id}/distribuicao").path("responses");
        for (String codigo : List.of("200", "400", "401", "403", "404", "409")) {
            assertThat(distribuicao.has(codigo)).as("distribuição %s", codigo).isTrue();
        }

        JsonNode detalhe = operacao("GET /api/demandas/{id}").path("responses");
        assertThat(detalhe.has("403")).as("fiscal fora do escopo").isTrue();
        assertThat(detalhe.has("404")).isTrue();

        JsonNode upload = operacao("POST /api/documentos").path("responses");
        for (String codigo : List.of("201", "400", "403", "413", "415")) {
            assertThat(upload.has(codigo)).as("upload %s", codigo).isTrue();
        }

        JsonNode membros = operacao("GET /api/equipes/{id}/membros").path("responses");
        assertThat(membros.has("404")).isTrue();
        assertThat(membros.has("403")).isTrue();
    }

    @Test
    void criacoesRespondem201EExclusoesEExclusaoLogica204() throws Exception {
        assertThat(operacao("POST /api/demandas").path("responses").has("201")).isTrue();
        assertThat(operacao("POST /api/demandas/{id}/atividades").path("responses").has("201")).isTrue();
        assertThat(operacao("POST /api/documentos").path("responses").has("201")).isTrue();
        assertThat(operacao("POST /api/demandas/{id}/documentos").path("responses").has("201")).isTrue();
        assertThat(operacao("DELETE /api/demandas/{id}").path("responses").has("204")).isTrue();
        assertThat(operacao("DELETE /api/demandas/{id}/documentos/{documentoId}").path("responses").has("204")).isTrue();
    }

    @Test
    void requestsEResponsesTemSchemasDeDto() throws Exception {
        JsonNode schemas = contrato().path("components").path("schemas");
        for (String dto : List.of("DemandaRequest", "DemandaDetalhe", "DemandaResumo", "HistoricoItem", "TriagemRequest",
                "DistribuicaoRequest", "AtividadeRequest", "FiscalizacaoRequest", "FiscalizacaoDetalhe",
                "ConclusaoRequest", "DevolucaoRequest", "DocumentoResumo", "DocumentoDetalhe", "VinculoDocumentoRequest",
                "ResumoIndicadores", "SerieMensalItem", "ContagemItem", "ContagemMunicipio", "MapaResponse", "PontoMapa",
                "DashboardResponse", "ItemCatalogo", "MunicipioDto", "SetorDto", "EquipeDto", "MembroDto",
                "PageResponseDemandaResumo", "PageResponseDocumentoResumo")) {
            assertThat(schemas.has(dto)).as("schema %s", dto).isTrue();
        }
        // Validações Bean Validation aparecem no schema (usadas pelo Web para formulários e mocks).
        JsonNode titulo = schemas.path("DemandaRequest").path("properties").path("titulo");
        assertThat(titulo.path("minLength").asInt()).isEqualTo(5);
        assertThat(titulo.path("maxLength").asInt()).isEqualTo(200);
        assertThat(schemas.path("DemandaRequest").path("required").toString())
                .contains("titulo", "descricao", "origem", "municipioId", "dataRecebimento");
    }

    @Test
    void enumsDoContratoSaoOsDosCatalogos() throws Exception {
        JsonNode props = contrato().path("components").path("schemas").path("DemandaDetalhe").path("properties");
        assertThat(props.path("status").path("enum").toString()).isEqualTo(
                "[\"PENDENTE\",\"EM_TRIAGEM\",\"AGUARDANDO_DISTRIBUICAO\",\"DISTRIBUIDA\",\"EM_ANDAMENTO\","
                        + "\"AGUARDANDO_CONCLUSAO\",\"FINALIZADA\"]");
        assertThat(props.path("origem").path("enum").toString()).isEqualTo(
                "[\"DENUNCIA\",\"MONITORAMENTO\",\"SOLICITACAO_INSTITUCIONAL\",\"ACAO_PLANEJADA\",\"ANALISE_AMBIENTAL\"]");
        assertThat(props.path("prioridade").path("enum").toString())
                .isEqualTo("[\"BAIXA\",\"MEDIA\",\"ALTA\",\"URGENTE\"]");
        assertThat(props.path("categoria").path("enum")).hasSize(8);
    }

    @Test
    void listagensDocumentamPaginacaoEFiltros() throws Exception {
        List<String> parametros = new ArrayList<>();
        operacao("GET /api/demandas").path("parameters").forEach(p -> parametros.add(p.path("name").asText()));
        assertThat(parametros).contains("q", "status", "municipioId", "categoria", "prioridade", "equipeId", "fiscalId",
                "atrasada", "de", "ate", "page", "size", "sort");
        // Os campos "sort" e "page"/"size" vêm do Pageable (?page&size&sort); paginação máxima de 500 está em application.yml.
        List<String> doc = new ArrayList<>();
        operacao("GET /api/documentos").path("parameters").forEach(p -> doc.add(p.path("name").asText()));
        assertThat(doc).contains("q", "categoria", "tipo", "municipioId", "demandaId", "de", "ate", "page", "size", "sort");
    }

    @Test
    void uploadEMultipartComArquivo() throws Exception {
        JsonNode corpo = operacao("POST /api/documentos").path("requestBody").path("content").path("multipart/form-data");
        assertThat(corpo.isMissingNode()).as("requestBody multipart/form-data").isFalse();
        assertThat(corpo.path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/DocumentoUploadRequest");
        JsonNode form = contrato().path("components").path("schemas").path("DocumentoUploadRequest");
        assertThat(form.path("properties").path("arquivo").path("format").asText()).isEqualTo("binary");
        assertThat(form.path("properties").has("titulo")).isTrue();
        assertThat(form.path("required").toString()).contains("arquivo", "titulo", "categoria", "tipo", "dataDocumento");
    }

    @Test
    void downloadDeArquivoEBinario() throws Exception {
        JsonNode ok = operacao("GET /api/documentos/{id}/arquivo").path("responses").path("200").path("content");
        assertThat(ok.path("application/octet-stream").path("schema").path("format").asText()).isEqualTo("binary");
    }

    @Test
    void todosOsEndpointsTemTagEResumo() throws Exception {
        for (String chave : ENDPOINTS_P0) {
            JsonNode op = operacao(chave);
            assertThat(op.path("tags")).as("tags de %s", chave).isNotEmpty();
            assertThat(op.path("summary").asText()).as("summary de %s", chave).isNotBlank();
        }
    }
}
