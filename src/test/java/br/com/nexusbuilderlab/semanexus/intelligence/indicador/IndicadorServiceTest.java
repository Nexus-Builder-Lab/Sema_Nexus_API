package br.com.nexusbuilderlab.semanexus.intelligence.indicador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.flux.demanda.Demanda;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorFiltro;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IndicadorServiceTest {

    static final LocalDate HOJE = LocalDate.of(2026, 11, 10);
    static final IndicadorFiltro SEM_FILTRO = new IndicadorFiltro(null, null, null, null, null, null, null);

    IndicadorRepository repo = mock(IndicadorRepository.class);
    IndicadorService service;
    Usuario analista = new Usuario("Analista", "a@exemplo.test", "hash", Perfil.ANALISTA);
    Municipio cuiaba = municipio(5103403, "Cuiabá", Bioma.CERRADO, "-15.601", "-56.097");
    Municipio sinop = municipio(5107909, "Sinop", Bioma.AMAZONIA, "-11.864", "-55.509");

    private br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository docs;

    @BeforeEach
    void preparar() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneId.of("UTC"));
        docs = mock(br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository.class);
        when(docs.contarAtivos()).thenReturn(12L);
        service = new IndicadorService(repo, clock, docs);
    }

    static Municipio municipio(int id, String nome, Bioma bioma, String lat, String lon) {
        Municipio m = mock(Municipio.class);
        when(m.getId()).thenReturn(id);
        when(m.getNome()).thenReturn(nome);
        when(m.getBioma()).thenReturn(bioma);
        when(m.getLatitude()).thenReturn(new BigDecimal(lat));
        when(m.getLongitude()).thenReturn(new BigDecimal(lon));
        return m;
    }

    Demanda demanda(long id, Municipio m, StatusDemanda status, CategoriaFiscalizacao cat, LocalDate recebida,
                    LocalDate prazo, LocalDateTime concluida) {
        Demanda d = new Demanda("DEM-2026/" + id, analista);
        ReflectionTestUtils.setField(d, "id", id);
        d.setTitulo("Demanda " + id);
        d.setDescricao("fictícia");
        d.setOrigem(Origem.DENUNCIA);
        d.setMunicipio(m);
        d.setCategoria(cat);
        d.setStatus(status);
        d.setDataRecebimento(recebida);
        d.setPrazo(prazo);
        d.setConcluidaEm(concluida);
        return d;
    }

    List<Demanda> base() {
        return List.of(
                demanda(1, cuiaba, StatusDemanda.PENDENTE, CategoriaFiscalizacao.QUEIMADA_INCENDIO,
                        LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 1), null),
                demanda(2, cuiaba, StatusDemanda.EM_ANDAMENTO, null, LocalDate.of(2026, 9, 20), null, null),
                demanda(3, sinop, StatusDemanda.FINALIZADA, CategoriaFiscalizacao.QUEIMADA_INCENDIO,
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5), LocalDateTime.of(2026, 7, 11, 10, 0)),
                demanda(4, sinop, StatusDemanda.FINALIZADA, null,
                        LocalDate.of(2026, 9, 1), null, LocalDateTime.of(2026, 9, 5, 10, 0)));
    }

    @Test
    void resumoCalculaTotaisAtrasadasPercentualETempoMedio() {
        when(repo.demandas(any())).thenReturn(base());
        when(repo.contarFiscalizacoes(any())).thenReturn(3L);

        var r = service.resumo(SEM_FILTRO);

        assertThat(r.demonstrativo()).isTrue();
        assertThat(r.totalDemandas()).isEqualTo(4);
        assertThat(r.pendentes()).isEqualTo(1);
        assertThat(r.emAndamento()).isEqualTo(1);
        assertThat(r.finalizadas()).isEqualTo(2);
        assertThat(r.atrasadas()).isEqualTo(1);
        assertThat(r.totalDocumentos()).isEqualTo(12L);
        assertThat(r.fiscalizacoes()).isEqualTo(3);
        assertThat(r.percentualConclusao()).isEqualTo(50.0);
        assertThat(r.tempoMedioAtendimentoDias()).isEqualTo(7.0);
    }

    @Test
    void resumoSemDemandasZeraTudoENaoTemTempoMedio() {
        when(repo.demandas(any())).thenReturn(List.of());
        var r = service.resumo(SEM_FILTRO);
        assertThat(r.totalDemandas()).isZero();
        assertThat(r.percentualConclusao()).isZero();
        assertThat(r.tempoMedioAtendimentoDias()).isNull();
    }

    @Test
    void porSituacaoTrazOsSeteStatusNaOrdemDoFluxoComZeros() {
        when(repo.demandas(any())).thenReturn(base());
        var itens = service.porSituacao(SEM_FILTRO).itens();
        assertThat(itens).hasSize(7);
        assertThat(itens.get(0).codigo()).isEqualTo("PENDENTE");
        assertThat(itens.get(0).total()).isEqualTo(1);
        assertThat(itens.get(1).total()).isZero();
        assertThat(itens.get(6).codigo()).isEqualTo("FINALIZADA");
        assertThat(itens.get(6).total()).isEqualTo(2);
    }

    @Test
    void porMunicipioOrdenaDoMaiorParaOMenor() {
        List<Demanda> l = new ArrayList<>(base());
        l.add(demanda(5, sinop, StatusDemanda.PENDENTE, null, HOJE, null, null));
        when(repo.demandas(any())).thenReturn(l);
        var itens = service.porMunicipio(SEM_FILTRO).itens();
        assertThat(itens).extracting("municipio").containsExactly("Sinop", "Cuiabá");
        assertThat(itens.get(0).total()).isEqualTo(3);
    }

    @Test
    void porBiomaECategoriaIncluemZeros() {
        when(repo.demandas(any())).thenReturn(base());
        var biomas = service.porBioma(SEM_FILTRO).itens();
        assertThat(biomas).extracting("codigo").containsExactly("AMAZONIA", "CERRADO", "PANTANAL");
        assertThat(biomas).extracting("total").containsExactly(2L, 2L, 0L);
        var cats = service.porCategoria(SEM_FILTRO).itens();
        assertThat(cats).hasSize(CategoriaFiscalizacao.values().length);
        assertThat(cats.stream().mapToLong(c -> c.total()).sum()).isEqualTo(2);
    }

    @Test
    void serieMensalPreencheMesesSemDemandasComZero() {
        when(repo.demandas(any())).thenReturn(base());
        var itens = service.serieMensal(SEM_FILTRO).itens();
        assertThat(itens).extracting("mes").containsExactly("2026-07", "2026-08", "2026-09");
        assertThat(itens).extracting("cadastradas").containsExactly(1L, 0L, 3L);
        assertThat(itens).extracting("finalizadas").containsExactly(1L, 0L, 1L);
    }

    @Test
    void mapaUsaCoordenadaPropriaOuCentroideDoMunicipio() {
        Demanda comCoord = demanda(1, cuiaba, StatusDemanda.PENDENTE, null, HOJE, null, null);
        comCoord.setLatitude(new BigDecimal("-15.500000"));
        comCoord.setLongitude(new BigDecimal("-56.000000"));
        Demanda semCoord = demanda(2, sinop, StatusDemanda.PENDENTE, null, HOJE, LocalDate.of(2026, 11, 1), null);
        when(repo.demandas(any())).thenReturn(List.of(comCoord, semCoord));

        var m = service.mapa(SEM_FILTRO);

        assertThat(m.demonstrativo()).isTrue();
        assertThat(m.pontos().get(0).coordenadaAproximada()).isFalse();
        assertThat(m.pontos().get(0).latitude()).isEqualByComparingTo("-15.5");
        assertThat(m.pontos().get(1).coordenadaAproximada()).isTrue();
        assertThat(m.pontos().get(1).latitude()).isEqualByComparingTo("-11.864");
        assertThat(m.pontos().get(1).atrasada()).isTrue();
        assertThat(m.pontos().get(1).municipio()).isEqualTo("Sinop");
    }
}
