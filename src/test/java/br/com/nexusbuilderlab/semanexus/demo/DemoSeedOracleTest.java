package br.com.nexusbuilderlab.semanexus.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Seed demonstrativo contra Oracle real (Testcontainers): o runner já aplicou o seed na subida do contexto
 * (profile demo); aqui se confirma a idempotência e o login do usuário de teste.
 * Fora do build padrão: {@code mvn -B -ntp verify -Poracle}.
 */
@Tag("oracle")
@Testcontainers
// O profile demo exige JWT_SECRET; este valor é fictício e só existe no teste.
@SpringBootTest(properties = "semanexus.jwt.secret=segredo-ficticio-somente-para-teste-0123456789")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DemoSeedOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    @Autowired
    DemoSeedService seed;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;

    private int contar(String tabela) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Integer.class);
    }

    @Test
    void seedAplicadoNaSubidaERepetidoNaoDuplica() {
        int usuarios = contar("USUARIO");
        int setores = contar("SETOR");
        int equipes = contar("EQUIPE");
        int membros = contar("EQUIPE_MEMBRO");
        assertThat(usuarios).isEqualTo(6);
        assertThat(setores).isEqualTo(3);
        assertThat(equipes).isEqualTo(4);
        assertThat(membros).isEqualTo(6);

        assertThat(seed.aplicar().vazio()).isTrue();

        assertThat(contar("USUARIO")).isEqualTo(usuarios);
        assertThat(contar("SETOR")).isEqualTo(setores);
        assertThat(contar("EQUIPE")).isEqualTo(equipes);
        assertThat(contar("EQUIPE_MEMBRO")).isEqualTo(membros);
    }

    @Test
    void usuarioDeTesteTemHashBcryptEDemoMarcado() {
        String hash = jdbc.queryForObject(
                "SELECT SENHA_HASH FROM USUARIO WHERE EMAIL = 'admin@admin.com'", String.class);
        assertThat(hash).startsWith("$2a$").doesNotContain("12345678");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM USUARIO WHERE EMAIL = 'admin@admin.com' AND PERFIL = 'ADMIN' AND ATIVO AND DEMO",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM USUARIO WHERE NOT DEMO", Integer.class)).isZero();
    }

    @Test
    void loginComUsuarioDeTesteFunciona() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@admin.com\",\"senha\":\"12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil").value("ADMIN"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fiscal1@demo.semanexus.local\",\"senha\":\"Demo@Sema2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil").value("FISCAL"));
    }
}
