package br.com.nexusbuilderlab.semanexus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Autenticação ponta a ponta contra Oracle real (Testcontainers). Fora do build padrão:
 * {@code mvn -B -ntp verify -Poracle}. Usa usuários fictícios criados pelo próprio teste.
 */
@Tag("oracle")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class AuthOracleTest {

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
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper mapper;

    private Usuario criar(String email, boolean ativo) {
        Usuario u = new Usuario("Usuário de Teste", email, encoder.encode(SENHA), Perfil.GESTOR);
        u.setAtivo(ativo);
        return usuarios.save(u);
    }

    private static String corpo(String email, String senha) {
        return "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}";
    }

    @Test
    void loginMeLogoutGravamAuditoriaEAtualizamUltimoAcesso() throws Exception {
        Usuario u = criar("gestor.teste@exemplo.test", true);

        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Gestor.Teste@Exemplo.TEST", SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil").value("GESTOR"))
                .andExpect(jsonPath("$.expiresIn").value(28_800))
                .andReturn().getResponse().getContentAsString();
        JsonNode resposta = mapper.readTree(json);
        String bearer = "Bearer " + resposta.get("token").asText();

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("gestor.teste@exemplo.test"))
                .andExpect(content().string(not(containsString("senha"))));
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
        // Issue #134: o token foi revogado na hora (jti em TOKEN_REVOGADO) e deixa de valer, inclusive para novo logout.
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
        Integer revogados = jdbc.queryForObject(
                "SELECT COUNT(*) FROM TOKEN_REVOGADO WHERE USUARIO_ID = ? AND EXPIRA_EM > REVOGADO_EM",
                Integer.class, u.getId());
        assertThat(revogados).isEqualTo(1);

        Integer logins = jdbc.queryForObject(
                "SELECT COUNT(*) FROM AUDITORIA WHERE USUARIO_ID = ? AND ACAO = 'LOGIN'", Integer.class, u.getId());
        Integer logouts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM AUDITORIA WHERE USUARIO_ID = ? AND ACAO = 'LOGOUT'", Integer.class, u.getId());
        Integer comUltimoAcesso = jdbc.queryForObject(
                "SELECT COUNT(*) FROM USUARIO WHERE ID = ? AND ULTIMO_ACESSO IS NOT NULL", Integer.class, u.getId());
        assertThat(logins).isEqualTo(1);
        assertThat(logouts).isEqualTo(1);
        assertThat(comUltimoAcesso).isEqualTo(1);
        // Nenhum registro de auditoria deste usuário guarda senha ou token.
        Integer vazamentos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM AUDITORIA WHERE USUARIO_ID = ? AND (DETALHE LIKE ? OR DETALHE LIKE ?)",
                Integer.class, u.getId(), "%" + SENHA + "%", "%eyJ%");
        assertThat(vazamentos).isZero();
    }

    @Test
    void senhaErradaOuEmailInexistenteRetornam401ComMesmaMensagem() throws Exception {
        criar("outro.teste@exemplo.test", true);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("outro.teste@exemplo.test", "senha-errada-999")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("naoexiste@exemplo.test", SENHA)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void usuarioInativoNaoAutenticaETokenAntigoPerdeValidade() throws Exception {
        Usuario u = criar("inativo.teste@exemplo.test", true);
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("inativo.teste@exemplo.test", SENHA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + mapper.readTree(json).get("token").asText();

        u.setAtivo(false);
        usuarios.save(u);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("inativo.teste@exemplo.test", SENHA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Usuário inativo. Contate o administrador do sistema."));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegidoSemTokenRetorna401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
