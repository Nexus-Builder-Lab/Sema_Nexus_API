package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Consultas espaciais do Agente de Triagem (Issue #132) contra Oracle real: pré-filtro por caixa e período, ordenação
 * pelo centro, limite e leitura da geometria do DETER. Fora do build padrão: {@code mvn -B -ntp verify -Poracle}.
 */
@Tag("oracle")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "semanexus.jwt.secret=segredo-ficticio-somente-para-teste-0123456789")
@ActiveProfiles("demo")
class ReferenciaEspacialOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    @org.springframework.test.context.DynamicPropertySource
    static void armazenamento(org.springframework.test.context.DynamicPropertyRegistry registry)
            throws java.io.IOException {
        var dir = java.nio.file.Files.createTempDirectory("semanexus-espacial");
        registry.add("semanexus.storage.dir", dir::toString);
    }

    @Autowired
    ReferenciaSateliteService referencia;

    private static final LocalDateTime DE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime ATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    @Test
    void maisProximosNaCaixaOrdenaPeloCentroRespeitaLimiteEPeriodo() {
        var todos = referencia.maisProximosNaCaixa(TipoReferencia.FOCO_CALOR, -18.1, -7.3, -61.7, -50.1, DE, ATE,
                -11.86, -55.5, 50);
        assertThat(todos).hasSize(50);
        double cos = Math.cos(Math.toRadians(-11.86));
        double anterior = -1;
        for (var p : todos) {
            double d = Math.pow(p.latitude() + 11.86, 2) + Math.pow((p.longitude() + 55.5) * cos, 2);
            assertThat(d).isGreaterThanOrEqualTo(anterior - 1e-9);
            anterior = d;
        }
        assertThat(referencia.maisProximosNaCaixa(TipoReferencia.FOCO_CALOR, -18.1, -7.3, -61.7, -50.1, DE,
                LocalDateTime.of(2000, 1, 2, 0, 0), -11.86, -55.5, 50)).isEmpty();
    }

    @Test
    void geometriaDoDeterELidaPeloId() {
        var deter = referencia.maisProximosNaCaixa(TipoReferencia.DESMATAMENTO, -18.1, -7.3, -61.7, -50.1, DE, ATE,
                -11.86, -55.5, 3);
        assertThat(deter).hasSize(3);
        assertThat(referencia.geometria(deter.get(0).id())).contains("coordinates");
    }
}
