package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.Demanda;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaHistorico;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaHistoricoRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteRepository;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteRepository.Linha;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.TipoReferencia;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.ConsultasSentinela.Janela;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.RegrasSentinela.ContagemInpe;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.RegrasSentinela.ItemFila;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Consultas da Sentinela (Issue #138) contra Oracle real (Testcontainers): última data do INPE, contagem por janela,
 * pontos do município, abertas por município/status e "desde quando" da fila (join com o histórico). Fora do build
 * padrão: {@code mvn -B -ntp verify -Poracle}. Exige Docker.
 */
@Tag("oracle")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ConsultasSentinelaOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    static final int SINOP = 5107909;
    private static final AtomicInteger SEQ = new AtomicInteger(9500);

    @Autowired
    ConsultasSentinela consultas;
    @Autowired
    ReferenciaSateliteRepository referencia;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    DemandaRepository demandas;
    @Autowired
    DemandaHistoricoRepository historico;
    @Autowired
    MunicipioRepository municipios;
    @Autowired
    JdbcTemplate jdbc;

    private static Linha linha(TipoReferencia tipo, LocalDateTime quando, String lat, String lon) {
        return new Linha(tipo, quando, new BigDecimal(lat), new BigDecimal(lon), SINOP, "AMAZONIA", "TESTE", null,
                null, null, null);
    }

    private Demanda demanda(Usuario autor, StatusDemanda status) {
        Demanda d = new Demanda("DEM-2026/" + SEQ.incrementAndGet(), autor);
        d.setTitulo("Demanda fictícia " + d.getNumero());
        d.setDescricao("Descrição fictícia");
        d.setOrigem(Origem.DENUNCIA);
        d.setMunicipio(municipios.findById(SINOP).orElseThrow());
        d.setDataRecebimento(LocalDate.now());
        d.setStatus(status);
        return demandas.save(d);
    }

    @Test
    void consultasDoInpeUsamAJanelaRelativaAoFimDoPacote() {
        // Datas de 2030: ficam depois de qualquer pacote real carregado no banco de teste.
        LocalDateTime fim = LocalDateTime.of(2030, 6, 30, 18, 0);
        referencia.gravar("Teste", TipoReferencia.FOCO_CALOR, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 6, 30),
                "https://exemplo.test", "teste", "focos.csv", "a".repeat(63) + UUID.randomUUID().toString().charAt(0),
                List.of(linha(TipoReferencia.FOCO_CALOR, fim, "-11.85", "-55.50"),
                        linha(TipoReferencia.FOCO_CALOR, fim.minusDays(10), "-11.86", "-55.51"),
                        linha(TipoReferencia.FOCO_CALOR, fim.minusDays(45), "-11.87", "-55.52")));

        assertThat(consultas.ultimaDataInpe(TipoReferencia.FOCO_CALOR)).contains(fim);
        List<Janela> janelas = List.of(new Janela(TipoReferencia.FOCO_CALOR, fim.minusDays(30), fim));
        assertThat(consultas.inpePorMunicipio(janelas)).filteredOn(c -> c.municipioId() == SINOP)
                .singleElement().extracting(ContagemInpe::focos).isEqualTo(2L);
        assertThat(consultas.pontosInpe(SINOP, janelas)).hasSize(2);
        assertThat(consultas.inpePorMunicipio(List.of())).isEmpty();
    }

    @Test
    void abertasEFilaComDataDoHistorico() {
        Usuario autor = usuarios.save(new Usuario("Autor", "u." + UUID.randomUUID() + "@exemplo.test",
                "hash-ficticio", Perfil.GESTOR));
        long abertasAntes = consultas.abertasPorMunicipio().getOrDefault(SINOP, 0L);
        long triagemAntes = consultas.abertasPorStatus().get("EM_TRIAGEM");

        Demanda triagem = demanda(autor, StatusDemanda.EM_TRIAGEM);
        DemandaHistorico h = historico.save(new DemandaHistorico(triagem, TipoHistorico.TRIAGEM,
                StatusDemanda.PENDENTE, StatusDemanda.EM_TRIAGEM, "Triagem iniciada", autor));
        LocalDateTime vinteDias = LocalDateTime.now().minusDays(20).withNano(0);
        jdbc.update("UPDATE DEMANDA_HISTORICO SET DATA = ? WHERE ID = ?", java.sql.Timestamp.valueOf(vinteDias),
                h.getId());
        Demanda pendente = demanda(autor, StatusDemanda.PENDENTE);
        demanda(autor, StatusDemanda.FINALIZADA);

        assertThat(consultas.abertasPorMunicipio().get(SINOP)).isEqualTo(abertasAntes + 2);
        assertThat(consultas.abertasPorStatus()).containsEntry("EM_TRIAGEM", triagemAntes + 1)
                .doesNotContainKey("FINALIZADA");
        List<ItemFila> itens = consultas.itensFila();
        assertThat(itens).filteredOn(i -> i.id() == triagem.getId()).singleElement()
                .extracting(ItemFila::desde).isEqualTo(vinteDias);
        assertThat(itens).filteredOn(i -> i.id() == pendente.getId()).singleElement()
                .satisfies(i -> assertThat(i.desde()).isNotNull());
    }
}
