package br.com.nexusbuilderlab.semanexus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.nexusbuilderlab.semanexus.core.auditoria.Auditoria;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditoriaRepository;
import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.setor.SetorRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Integração com Oracle real (Testcontainers, Oracle 23ai Free). Fora do build padrão:
 * {@code mvn -B -ntp verify -Poracle}. Exige Docker.
 *
 * <p>Sobe o contexto completo com {@code ddl-auto=validate}: se o mapeamento JPA divergir do schema
 * criado pelo Flyway, o contexto nem inicia.
 */
@Tag("oracle")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class CoreSchemaOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    MunicipioRepository municipios;

    @Autowired
    SetorRepository setores;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    EquipeRepository equipes;

    @Autowired
    AuditoriaRepository auditorias;

    @Test
    void flywayAplicouV1eV2ENaoReaplica() {
        List<String> aplicadas = java.util.Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(Object::toString)
                .toList();
        assertThat(aplicadas).contains("1", "2");

        MigrateResult novamente = flyway.migrate();
        assertThat(novamente.migrationsExecuted).isZero();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void carrega142MunicipiosTodosDeMatoGrosso() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM MUNICIPIO", Integer.class)).isEqualTo(142);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM MUNICIPIO WHERE ID NOT BETWEEN 5100000 AND 5199999", Integer.class)).isZero();
        assertThat(municipios.count()).isEqualTo(142);
        assertThat(municipios.findById(5103403)).hasValueSatisfying(m -> assertThat(m.getNome()).isEqualTo("Cuiabá"));
        assertThat(municipios.findByBiomaOrderByNomeAsc(Bioma.PANTANAL)).isNotEmpty();
    }

    @Test
    void persisteSetorUsuarioEquipeEAuditoria() {
        tx.executeWithoutResult(s -> {
            Setor setor = setores.save(new Setor("CTF-IT", "Coordenadoria Fictícia de Teste"));
            Usuario fiscal = new Usuario("Fiscal Fictício", "Fiscal.IT@Exemplo.com", "$2a$10$hashficticio", Perfil.FISCAL);
            fiscal.setSetor(setor);
            usuarios.save(fiscal);

            Equipe equipe = new Equipe("Equipe Fictícia IT", setor);
            equipe.setResponsavel(fiscal);
            equipe.getMembros().add(fiscal);
            equipes.save(equipe);

            auditorias.save(new Auditoria(fiscal.getId(), "CRIAR", "EQUIPE", null, "teste de integração"));
        });

        assertThat(usuarios.findByEmail("fiscal.it@exemplo.com")).isPresent();
        Long setorId = setores.findBySigla("CTF-IT").orElseThrow().getId();
        assertThat(equipes.findBySetorIdOrderByNomeAsc(setorId)).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM EQUIPE_MEMBRO WHERE EQUIPE_ID = ?", Integer.class,
                equipes.findBySetorIdOrderByNomeAsc(setorId).get(0).getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM AUDITORIA WHERE ACAO = 'CRIAR'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void checksRejeitamPerfilEBiomaInvalidosEEmailDuplicado() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO USUARIO (NOME, EMAIL, SENHA_HASH, PERFIL) VALUES (?, ?, ?, ?)",
                "X", "perfil.invalido@exemplo.com", "h", "SUPERUSER"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO MUNICIPIO (ID, NOME, REGIAO, BIOMA, LATITUDE, LONGITUDE) VALUES (?, ?, ?, ?, ?, ?)",
                5199998, "Fictício", "Cuiabá", "CAATINGA", -15.0, -56.0))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO MUNICIPIO (ID, NOME, REGIAO, BIOMA, LATITUDE, LONGITUDE) VALUES (?, ?, ?, ?, ?, ?)",
                3550308, "Fora de MT", "Cuiabá", "CERRADO", -15.0, -56.0))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("INSERT INTO USUARIO (NOME, EMAIL, SENHA_HASH, PERFIL) VALUES (?, ?, ?, ?)",
                "Dup", "duplicado@exemplo.com", "h", "CONSULTA");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO USUARIO (NOME, EMAIL, SENHA_HASH, PERFIL) VALUES (?, ?, ?, ?)",
                "Dup2", "duplicado@exemplo.com", "h", "CONSULTA"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
