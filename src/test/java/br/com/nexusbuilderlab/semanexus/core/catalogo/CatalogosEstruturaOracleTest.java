package br.com.nexusbuilderlab.semanexus.core.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Catálogos e estrutura (Issue #7) contra Oracle real (Testcontainers), com login real e o seed demonstrativo
 * do profile {@code dev}. Fora do build padrão: {@code mvn -B -ntp verify -Poracle}.
 */
@Tag("oracle")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CatalogosEstruturaOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    // Senha de teste fictícia, usada só aqui.
    static final String SENHA = "senha-de-teste-123";

    @Autowired
    MockMvc mvc;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    ObjectMapper mapper;

    private String token(Perfil perfil) throws Exception {
        String email = "catalogos." + perfil.name().toLowerCase() + "@exemplo.test";
        if (usuarios.findByEmail(email).isEmpty()) {
            usuarios.save(new Usuario("Teste " + perfil, email, encoder.encode(SENHA), perfil));
        }
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(json).get("token").asText();
    }

    private JsonNode obter(Perfil perfil, String caminho) throws Exception {
        String json = mvc.perform(get(caminho).header(HttpHeaders.AUTHORIZATION, token(perfil)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    @Test
    void municipiosRetornam142DeMatoGrossoEmOrdemAlfabetica() throws Exception {
        JsonNode lista = obter(Perfil.CONSULTA, "/api/catalogos/municipios");

        assertThat(lista).hasSize(142);
        List<String> nomes = new ArrayList<>();
        lista.forEach(m -> {
            nomes.add(m.get("nome").asText());
            assertThat(m.get("id").asInt()).isBetween(5100000, 5199999);
            assertThat(m.get("bioma").asText()).isIn("AMAZONIA", "CERRADO", "PANTANAL");
            assertThat(m.get("regiao").asText()).isNotBlank();
        });
        assertThat(nomes).isSortedAccordingTo(Comparator.comparing(CatalogoService::chaveDeOrdenacao));
        assertThat(nomes.indexOf("Água Boa")).isLessThan(nomes.indexOf("Alta Floresta")).isGreaterThan(nomes.indexOf("Acorizal"));
        JsonNode cuiaba = null;
        for (JsonNode m : lista) {
            if (m.get("id").asInt() == 5103403) {
                cuiaba = m;
            }
        }
        assertThat(cuiaba).isNotNull();
        assertThat(cuiaba.get("nome").asText()).isEqualTo("Cuiabá");
    }

    @Test
    void filtroPorBiomaParticionaOsMunicipios() throws Exception {
        int soma = 0;
        for (String bioma : List.of("AMAZONIA", "CERRADO", "PANTANAL")) {
            JsonNode lista = obter(Perfil.FISCAL, "/api/catalogos/municipios?bioma=" + bioma);
            lista.forEach(m -> assertThat(m.get("bioma").asText()).isEqualTo(bioma));
            soma += lista.size();
        }
        assertThat(soma).isEqualTo(142);
    }

    @Test
    void catalogosDeEnumRespondemComOsValoresDaEspecificacao() throws Exception {
        assertThat(obter(Perfil.CONSULTA, "/api/catalogos/status")).hasSize(7);
        assertThat(obter(Perfil.CONSULTA, "/api/catalogos/origens")).hasSize(5);
        assertThat(obter(Perfil.CONSULTA, "/api/catalogos/categorias")).hasSize(8);
        assertThat(obter(Perfil.CONSULTA, "/api/catalogos/prioridades")).hasSize(4);
        assertThat(obter(Perfil.CONSULTA, "/api/catalogos/biomas")).hasSize(3);
        assertThat(obter(Perfil.CONSULTA, "/api/catalogos/status").get(2).get("codigo").asText())
                .isEqualTo("AGUARDANDO_DISTRIBUICAO");
    }

    @Test
    void setoresEEquipesDoSeedParaGestorEBloqueadosParaConsulta() throws Exception {
        JsonNode setores = obter(Perfil.GESTOR, "/api/setores");
        List<String> siglas = new ArrayList<>();
        setores.forEach(s -> siglas.add(s.get("sigla").asText()));
        assertThat(siglas).contains("DEMO-GAB", "DEMO-FISC", "DEMO-DOC");
        assertThat(siglas).isSorted();

        JsonNode equipes = obter(Perfil.GESTOR, "/api/equipes");
        assertThat(equipes.size()).isGreaterThanOrEqualTo(4);
        long totalNosDados = 0;
        for (JsonNode equipe : equipes) {
            long id = equipe.get("id").asLong();
            JsonNode membros = obter(Perfil.GESTOR, "/api/equipes/" + id + "/membros");
            assertThat(membros.size()).as("membros da equipe %s", id).isEqualTo(equipe.get("totalMembros").asInt());
            membros.forEach(m -> {
                assertThat(m.has("senhaHash")).isFalse();
                assertThat(m.has("senha")).isFalse();
                assertThat(m.get("email").asText()).isNotBlank();
            });
            totalNosDados += membros.size();
        }
        assertThat(totalNosDados).isGreaterThanOrEqualTo(6);
        // Equipe do seed com responsável e setor resolvidos (fetch join).
        JsonNode alfa = null;
        for (JsonNode e : equipes) {
            if (e.get("nome").asText().startsWith("Equipe Alfa")) {
                alfa = e;
            }
        }
        assertThat(alfa).isNotNull();
        assertThat(alfa.get("setorSigla").asText()).isEqualTo("DEMO-FISC");
        assertThat(alfa.get("responsavel").get("nome").asText()).isEqualTo("Fiscal Demonstrativo 1");

        JsonNode doSetorFisc = obter(Perfil.ADMIN, "/api/equipes?setorId=" + alfa.get("setorId").asLong() + "&ativo=true");
        assertThat(doSetorFisc.size()).isGreaterThanOrEqualTo(3);
        doSetorFisc.forEach(e -> assertThat(e.get("setorSigla").asText()).isEqualTo("DEMO-FISC"));

        mvc.perform(get("/api/setores").header(HttpHeaders.AUTHORIZATION, token(Perfil.CONSULTA)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/equipes").header(HttpHeaders.AUTHORIZATION, token(Perfil.FISCAL)))
                .andExpect(status().isForbidden());
    }

    @Test
    void equipeInexistenteRetorna404EContratoPendenteRetorna501() throws Exception {
        mvc.perform(get("/api/equipes/9999999/membros").header(HttpHeaders.AUTHORIZATION, token(Perfil.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION, token(Perfil.CONSULTA)))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.issue").value(17));
    }
}
