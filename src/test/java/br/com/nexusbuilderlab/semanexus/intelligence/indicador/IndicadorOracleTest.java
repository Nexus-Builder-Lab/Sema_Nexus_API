package br.com.nexusbuilderlab.semanexus.intelligence.indicador;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.flux.demanda.Demanda;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaRequest;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorFiltro;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
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
    @Autowired
    TransactionTemplate tx;

    private Long cadastrarId(UsuarioAutenticado ator, int municipio, CategoriaFiscalizacao categoria, LocalDate data) {
        return demandas.cadastrar(ator, new DemandaRequest("Indicador " + UUID.randomUUID(), "Descrição fictícia",
                Origem.DENUNCIA, categoria, municipio, null, null, null, data, null, null, null)).id();
    }

    private void cadastrar(UsuarioAutenticado ator, int municipio, CategoriaFiscalizacao categoria, LocalDate data) {
        cadastrarId(ator, municipio, categoria, data);
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

    @Test
    void cadastrarEFinalizarAlteramResumoEPorSituacao() {
        Usuario u = usuarios.save(new Usuario("Analista", "fin." + UUID.randomUUID() + "@exemplo.test", "hash", Perfil.ANALISTA));
        UsuarioAutenticado ator = UsuarioAutenticado.de(u);
        IndicadorFiltro todos = new IndicadorFiltro(null, null, null, null, null, null);

        var r0 = indicadores.resumo(todos);
        long pend0 = situacao(todos, "PENDENTE");
        long fin0 = situacao(todos, "FINALIZADA");

        Long id = cadastrarId(ator, CUIABA, CategoriaFiscalizacao.QUEIMADA_INCENDIO,
                LocalDate.now(ZoneId.of("America/Cuiaba")).minusDays(2));
        var r1 = indicadores.resumo(todos);
        assertThat(r1.totalDemandas()).isEqualTo(r0.totalDemandas() + 1);
        assertThat(r1.pendentes()).isEqualTo(r0.pendentes() + 1);
        assertThat(situacao(todos, "PENDENTE")).isEqualTo(pend0 + 1);

        // Conclusão aplicada direto na entidade: o fluxo completo (triagem, equipe, fiscalização) é coberto pelos
        // testes do Flux; aqui interessa apenas que o Intelligence reflita o novo status na consulta seguinte.
        tx.executeWithoutResult(t -> {
            Demanda d = em.find(Demanda.class, id);
            d.setStatus(StatusDemanda.FINALIZADA);
            d.setConcluidaEm(LocalDateTime.now());
        });

        var r2 = indicadores.resumo(todos);
        assertThat(r2.totalDemandas()).isEqualTo(r1.totalDemandas());
        assertThat(r2.finalizadas()).isEqualTo(r0.finalizadas() + 1);
        assertThat(r2.pendentes()).isEqualTo(r0.pendentes());
        assertThat(situacao(todos, "PENDENTE")).isEqualTo(pend0);
        assertThat(situacao(todos, "FINALIZADA")).isEqualTo(fin0 + 1);
        assertThat(r2.percentualConclusao()).isGreaterThan(0);
    }

    private long situacao(IndicadorFiltro filtro, String codigo) {
        return indicadores.porSituacao(filtro).itens().stream()
                .filter(i -> i.codigo().equals(codigo)).findFirst().orElseThrow().total();
    }
}
