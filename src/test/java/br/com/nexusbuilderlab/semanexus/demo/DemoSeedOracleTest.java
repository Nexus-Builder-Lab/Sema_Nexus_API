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
@Testcontainers(disabledWithoutDocker = true)
// O profile demo exige JWT_SECRET; este valor é fictício e só existe no teste.
@SpringBootTest(properties = "semanexus.jwt.secret=segredo-ficticio-somente-para-teste-0123456789")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DemoSeedOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    @org.springframework.test.context.DynamicPropertySource
    static void armazenamento(org.springframework.test.context.DynamicPropertyRegistry registry)
            throws java.io.IOException {
        var dir = java.nio.file.Files.createTempDirectory("semanexus-demo");
        registry.add("semanexus.storage.dir", dir::toString);
    }

    @Autowired
    DemoMassaService massa;
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
        assertThat(usuarios).isEqualTo(16);
        assertThat(setores).isEqualTo(8);
        assertThat(equipes).isEqualTo(9);
        assertThat(membros).isEqualTo(16);

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
    void massaDemonstrativaCobreStatusBiomasEDocumentosERepetidaNaoDuplica() {
        int demandas = contar("DEMANDA");
        int documentos = contar("DOCUMENTO");
        int historico = contar("DEMANDA_HISTORICO");
        assertThat(demandas).isEqualTo(DemoMassaGerador.TOTAL);
        assertThat(documentos).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT STATUS) FROM DEMANDA", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT m.BIOMA) FROM DEMANDA d JOIN MUNICIPIO m "
                + "ON m.ID = d.MUNICIPIO_ID", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT MUNICIPIO_ID) FROM DEMANDA", Integer.class))
                .isGreaterThanOrEqualTo(25);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM DEMANDA WHERE PRAZO < TRUNC(SYSDATE) "
                + "AND STATUS <> 'FINALIZADA'", Integer.class)).isPositive();
        assertThat(contar("FISCALIZACAO")).isEqualTo(jdbc.queryForObject(
                "SELECT COUNT(*) FROM DEMANDA WHERE STATUS IN ('AGUARDANDO_CONCLUSAO', 'FINALIZADA')", Integer.class));
        assertThat(contar("DOCUMENTO_DEMANDA")).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM DOCUMENTO WHERE TITULO LIKE 'Parecer Técnico nº 014/2026%'",
                Integer.class)).isEqualTo(2);

        assertThat(massa.aplicar().vazio()).isTrue();

        assertThat(contar("DEMANDA")).isEqualTo(demandas);
        assertThat(contar("DOCUMENTO")).isEqualTo(documentos);
        assertThat(contar("DEMANDA_HISTORICO")).isEqualTo(historico);
    }

    @Test
    void demandasDistribuidasPertencemAoNucleoDaRegiaoDoMunicipio() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SETOR WHERE SIGLA LIKE 'NR-%'", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM DEMANDA WHERE EQUIPE_ID IS NOT NULL", Integer.class))
                .isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM DEMANDA d JOIN MUNICIPIO m ON m.ID = d.MUNICIPIO_ID "
                + "JOIN EQUIPE e ON e.ID = d.EQUIPE_ID JOIN SETOR s ON s.ID = e.SETOR_ID "
                + "WHERE d.OBSERVACOES LIKE '[DEMO-SEED]%' AND s.SIGLA <> CASE m.REGIAO WHEN 'Cuiabá' THEN 'NR-CBA' "
                + "WHEN 'Cáceres' THEN 'NR-CAC' WHEN 'Rondonópolis' THEN 'NR-ROO' WHEN 'Barra do Garças' THEN 'NR-BAG' "
                + "ELSE 'NR-SNO' END", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM DEMANDA WHERE EQUIPE_ID IS NULL AND STATUS NOT IN "
                + "('PENDENTE', 'EM_TRIAGEM', 'AGUARDANDO_DISTRIBUICAO')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT c) FROM (SELECT COUNT(*) c FROM DEMANDA "
                + "WHERE EQUIPE_ID IS NOT NULL GROUP BY EQUIPE_ID)", Integer.class)).isGreaterThan(1);
    }

    @Test
    void loginComUsuarioDeTesteFunciona() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@admin.com\",\"senha\":\"12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil").value("ADMIN"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fiscal1@demo.semaalerta.local\",\"senha\":\"Demo@Sema2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil").value("FISCAL"));
    }
}
