package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.math.BigDecimal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Área afetada contra Oracle real (V7): o snapshot do INPE carregado na subida guarda o polígono e os atributos do
 * DETER e os atributos dos focos (sem área); a massa demonstrativa recebe polígonos FICTÍCIOS só nas categorias com
 * área. Fora do build padrão: {@code mvn -B -ntp verify -Poracle}.
 */
@Tag("oracle")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "semanexus.jwt.secret=segredo-ficticio-somente-para-teste-0123456789")
@ActiveProfiles("demo")
class AreaAfetadaOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    @org.springframework.test.context.DynamicPropertySource
    static void armazenamento(org.springframework.test.context.DynamicPropertyRegistry registry)
            throws java.io.IOException {
        var dir = java.nio.file.Files.createTempDirectory("semanexus-area");
        registry.add("semanexus.storage.dir", dir::toString);
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ReferenciaSateliteService referencia;
    @Autowired
    DemandaService demandas;
    @Autowired
    UsuarioRepository usuarios;

    private long contar(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    @Test
    void deterTemPoligonoEAreaEFocoSoPontoComAtributosDaFonte() {
        assertThat(contar("SELECT COUNT(*) FROM IMPORTACAO_REFERENCIA")).isEqualTo(2);
        long deter = contar("SELECT COUNT(*) FROM REFERENCIA_SATELITE WHERE TIPO = 'DESMATAMENTO'");
        assertThat(deter).isGreaterThan(1000);
        assertThat(contar("SELECT COUNT(*) FROM REFERENCIA_SATELITE WHERE TIPO = 'DESMATAMENTO' "
                + "AND GEOMETRIA IS NOT NULL AND AREA_KM2 IS NOT NULL")).isEqualTo(deter);
        assertThat(contar("SELECT COUNT(*) FROM REFERENCIA_SATELITE WHERE TIPO = 'FOCO_CALOR' "
                + "AND (GEOMETRIA IS NOT NULL OR AREA_KM2 IS NOT NULL)")).isZero();

        long idDeter = contar("SELECT MIN(ID) FROM REFERENCIA_SATELITE WHERE TIPO = 'DESMATAMENTO' "
                + "AND AREA_KM2 > 0.1");
        var d = referencia.detalhe(idDeter);
        assertThat(d.areaInformada()).isTrue();
        assertThat(d.geometria()).containsKey("coordinates");
        assertThat(d.ponto().areaHa()).isGreaterThan(BigDecimal.TEN);
        assertThat(d.areaKm2Fonte()).isNotNull();
        assertThat(d.atributos()).containsKeys("gid", "classname", "view_date");
        assertThat(d.importacao().arquivo()).isEqualTo("deter_mt.geojson.gz");

        long idFoco = contar("SELECT MIN(ID) FROM REFERENCIA_SATELITE WHERE TIPO = 'FOCO_CALOR'");
        var f = referencia.detalhe(idFoco);
        assertThat(f.areaInformada()).isFalse();
        assertThat(f.geometria()).isNull();
        assertThat(f.areaObservacao()).contains("não informada pela fonte");
        assertThat(f.atributos()).containsKeys("id", "frp");
    }

    @Test
    void massaDemonstrativaTemAreaFicticiaSoNasCategoriasComArea() {
        long comArea = contar("SELECT COUNT(*) FROM DEMANDA WHERE AREA_GEOMETRIA IS NOT NULL AND AREA_HA > 0");
        assertThat(comArea).isGreaterThan(20);
        assertThat(contar("SELECT COUNT(*) FROM DEMANDA WHERE AREA_GEOMETRIA IS NOT NULL AND CATEGORIA IN "
                + "('PESCA_IRREGULAR', 'FAUNA_SILVESTRE', 'RECURSOS_HIDRICOS', 'RESIDUOS_POLUICAO')")).isZero();

        long id = contar("SELECT MIN(ID) FROM DEMANDA WHERE AREA_GEOMETRIA IS NOT NULL");
        var admin = usuarios.findByEmail("admin@admin.com").orElseThrow();
        var detalhe = demandas.detalhar(UsuarioAutenticado.de(admin), id);
        assertThat(detalhe.areaHa()).isPositive();
        assertThat(detalhe.areaGeometria()).containsEntry("type", "Polygon");
    }
}
