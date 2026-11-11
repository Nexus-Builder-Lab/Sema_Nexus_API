package br.com.nexusbuilderlab.semanexus.intelligence.indicador;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaRequest;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorFiltro;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Issue #17 contra Oracle real: os indicadores batem com contagens SQL diretas, os filtros combinam e uma
 * demanda cadastrada altera o resumo na consulta seguinte. Fora do build padrão: {@code mvn verify -Poracle}.
 */
@Tag("oracle")
@Testcontainers
@SpringBootTest
class IndicadorOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    static final int CUIABA = 5103403;
    static final int SINOP = 5107909;

    @Autowired
    IndicadorService indicadores;
    @Autowired
    DemandaService demandas;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    EntityManager em;

    private void cadastrar(UsuarioAutenticado ator, int municipio, CategoriaFiscalizacao categoria, LocalDate data) {
        demandas.cadastrar(ator, new DemandaRequest("Indicador " + UUID.randomUUID(), "Descrição fictícia",
                Origem.DENUNCIA, categoria, municipio, null, null, null, data, null, null, null));
    }

    @Test
    void numerosBatemComSqlDiretoFiltrosCombinamECadastroAlteraOResumo() {
        Usuario u = usuarios.save(new Usuario("Analista", "ind." + UUID.randomUUID() + "@exemplo.test", "hash", Perfil.ANALISTA));
        UsuarioAutenticado ator = UsuarioAutenticado.de(u);
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Cuiaba"));
        IndicadorFiltro todos = new IndicadorFiltro(null, null, null, null, null, null);

        long antes = indicadores.resumo(todos).totalDemandas();
        cadastrar(ator, CUIABA, CategoriaFiscalizacao.QUEIMADA_INCENDIO, hoje.minusDays(3));
        cadastrar(ator, SINOP, null, hoje.minusDays(1));

        var depois = indicadores.resumo(todos);
        long sql = ((Number) em.createNativeQuery("select count(*) from DEMANDA where EXCLUIDO_EM is null")
                .getSingleResult()).longValue();
        assertThat(depois.totalDemandas()).isEqualTo(antes + 2).isEqualTo(sql);
        assertThat(indicadores.porSituacao(todos).itens().get(0).total()).isGreaterThanOrEqualTo(2);
        assertThat(indicadores.mapa(todos).pontos()).hasSize((int) sql);

        var combinado = new IndicadorFiltro(hoje.minusDays(5), hoje, CUIABA, null,
                CategoriaFiscalizacao.QUEIMADA_INCENDIO, null);
        long sqlCombinado = ((Number) em.createNativeQuery("""
                select count(*) from DEMANDA where EXCLUIDO_EM is null and MUNICIPIO_ID = :m
                and CATEGORIA = 'QUEIMADA_INCENDIO' and DATA_RECEBIMENTO between :de and :ate""")
                .setParameter("m", CUIABA).setParameter("de", hoje.minusDays(5)).setParameter("ate", hoje)
                .getSingleResult()).longValue();
        assertThat(indicadores.resumo(combinado).totalDemandas()).isEqualTo(sqlCombinado).isGreaterThanOrEqualTo(1);
    }
}
