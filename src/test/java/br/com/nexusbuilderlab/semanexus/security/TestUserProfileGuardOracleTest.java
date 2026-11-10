package br.com.nexusbuilderlab.semanexus.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.nexusbuilderlab.semanexus.SemaNexusApplication;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Trava do usuário de teste contra Oracle real (Testcontainers): sobe a aplicação de verdade
 * ({@code SpringApplication}) em cada profile e confere se a inicialização é aceita ou recusada.
 * Fora do build padrão: {@code mvn -B -ntp verify -Poracle}.
 */
@Tag("oracle")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestUserProfileGuardOracleTest {

    @Container
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    private ConfigurableApplicationContext subir(String profile) {
        return new SpringApplicationBuilder(SemaNexusApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles(profile)
                // Argumentos de linha de comando têm precedência sobre application*.yml e variáveis de ambiente.
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + ORACLE.getJdbcUrl(),
                        "--spring.datasource.username=" + ORACLE.getUsername(),
                        "--spring.datasource.password=" + ORACLE.getPassword(),
                        // valor fictício, só existe no teste (prod exige JWT_SECRET)
                        "--semanexus.jwt.secret=segredo-ficticio-somente-para-teste-0123456789");
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword()));
    }

    @Test
    @Order(1)
    void prodSemUsuarioDeTesteSobe() {
        try (ConfigurableApplicationContext ctx = subir("prod")) {
            assertThat(ctx.isRunning()).isTrue();
        }
        assertThat(jdbc().queryForObject(
                "SELECT COUNT(*) FROM USUARIO WHERE EMAIL = 'admin@admin.com'", Integer.class)).isZero();
    }

    @Test
    @Order(2)
    void demoCriaUsuarioDeTesteESobe() {
        try (ConfigurableApplicationContext ctx = subir("demo")) {
            assertThat(ctx.isRunning()).isTrue();
        }
        assertThat(jdbc().queryForObject(
                "SELECT COUNT(*) FROM USUARIO WHERE EMAIL = 'admin@admin.com'", Integer.class)).isEqualTo(1);
    }

    @Test
    @Order(3)
    void prodComUsuarioDeTesteRecusaIniciar() {
        assertThatThrownBy(() -> subir("prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin@admin.com")
                .hasMessageContaining("demo/dev");
    }

    @Test
    @Order(4)
    void devComUsuarioDeTesteSobe() {
        try (ConfigurableApplicationContext ctx = subir("dev")) {
            assertThat(ctx.isRunning()).isTrue();
        }
    }
}
