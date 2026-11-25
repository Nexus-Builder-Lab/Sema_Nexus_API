package br.com.nexusbuilderlab.semanexus.intelligence.camada;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Validação em Oracle 23ai Free real da migration {@code V16__camadas_publicas_mt.sql}, carga offline idempotente e
 * consulta espacial de sobreposição. Fora do build padrão: {@code mvn -B -ntp verify -Poracle}.
 */
@Tag("oracle")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "semanexus.jwt.secret=segredo-ficticio-somente-para-teste-0123456789")
@ActiveProfiles("demo")
class CamadaPublicaOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    @org.springframework.test.context.DynamicPropertySource
    static void armazenamento(org.springframework.test.context.DynamicPropertyRegistry registry)
            throws java.io.IOException {
        var dir = java.nio.file.Files.createTempDirectory("semanexus-camadas");
        registry.add("semanexus.storage.dir", dir::toString);
    }

    @Autowired
    CamadaPublicaLoader loader;
    @Autowired
    CamadaPublicaService service;

    @Test
    void snapshotCarregaSemDuplicarEAtendeGeoJsonESobreposicaoNoOracleReal() {
        assertThat(loader.carregarSnapshot()).isZero(); // já carregado pelo Runner na subida
        var meta = service.metadados();
        assertThat(meta.oficialSema()).isFalse();
        assertThat(meta.importacoes()).hasSize(3);

        var ucs = service.geojson(TipoCamadaPublica.UNIDADE_CONSERVACAO, null, null, null, 100);
        assertThat(ucs.oficialSema()).isFalse();
        assertThat(ucs.colecao().features()).isNotEmpty();

        // Região do Pantanal / Cuiabá — verifica consulta por caixa e distância real
        var sob = service.sobreposicaoPonto(-15.60, -56.10, 25.0);
        assertThat(sob.oficialSema()).isFalse();
        assertThat(sob.hidrografia()).isNotEmpty();
    }
}
