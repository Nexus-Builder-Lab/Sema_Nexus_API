package br.com.nexusbuilderlab.semanexus.flux.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaProxima;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaResumo;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.PorMunicipioResponse;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteRepository.PontoEspacial;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteService;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.TipoReferencia;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AnaliseEspacialTest {

    private static final double LAT = -11.86;
    private static final double LON = -55.50;
    private final DemandaService demandas = mock(DemandaService.class);
    private final ReferenciaSateliteService ref = mock(ReferenciaSateliteService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UsuarioAutenticado ator = mock(UsuarioAutenticado.class);
    private final AnaliseEspacial analise = new AnaliseEspacial(demandas, ref, jdbc);

    private static DemandaDetalhe demanda(BigDecimal lat, BigDecimal lon, Map<String, Object> area) {
        return new DemandaDetalhe(4L, "DEM-2026/0004", "Queimada", "Fogo", Origem.values()[0], null,
                new MunicipioRef(5107909, "Sinop", Bioma.AMAZONIA), null, lat, lon, null, area, LocalDate.now(), null,
                null, null, StatusDemanda.PENDENTE, false, null, null, null, null, null, null, null, 0, null, null, null);
    }

    private static DemandaResumo resumo(long id, String numero, String municipio) {
        return new DemandaResumo(id, numero, "Fogo", new MunicipioRef(1, municipio, Bioma.AMAZONIA), Origem.values()[0],
                null, null, StatusDemanda.PENDENTE, LocalDate.now(), null, false, null, null, null, null);
    }

    private void totaisDoMunicipio() {
        when(ref.porMunicipio(any())).thenReturn(new PorMunicipioResponse("INPE", false, null, "", 7, List.of()));
    }

    @Test
    void contaFocosNoRaioEIgnoraOsForaDoRaioMesmoDentroDaCaixa() {
        totaisDoMunicipio();
        // foco a cerca de 2,2 km (dentro de 5 km) e foco na diagonal da caixa a cerca de 7 km (fora de 5 km)
        when(ref.naCaixa(eq(TipoReferencia.FOCO_CALOR), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                eq(false), anyInt())).thenReturn(List.of(
                new PontoEspacial(1, LAT + 0.02, LON, null, null),
                new PontoEspacial(2, LAT + 0.0445, LON + 0.0445, null, null)));
        var i = analise.inpe(demanda(BigDecimal.valueOf(LAT), BigDecimal.valueOf(LON), null), 5);
        assertThat(i.focosNoRaio()).isEqualTo(1);
        assertThat(i.focoMaisProximoKm()).isBetween(2.1, 2.3);
        assertThat(i.focosMunicipio()).isEqualTo(7);
    }

    @Test
    void deterIntersectaOuFicaADistancia() {
        totaisDoMunicipio();
        Map<String, Object> dentro = quadrado(LON - 0.01, LAT - 0.01, 0.02);   // contem o ponto
        Map<String, Object> perto = quadrado(LON + 0.05, LAT - 0.01, 0.02);    // borda a cerca de 5,4 km a leste
        Map<String, Object> longe = quadrado(LON + 0.5, LAT, 0.02);            // cerca de 55 km
        when(ref.naCaixa(eq(TipoReferencia.DESMATAMENTO), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                eq(true), anyInt())).thenReturn(List.of(
                new PontoEspacial(1, LAT, LON, null, json(dentro)),
                new PontoEspacial(2, LAT, LON + 0.06, null, json(perto)),
                new PontoEspacial(3, LAT, LON + 0.5, null, json(longe))));
        var d = demanda(BigDecimal.valueOf(LAT), BigDecimal.valueOf(LON), null);
        var i10 = analise.inpe(d, 10);
        assertThat(i10.alertasDeterNoRaio()).isEqualTo(2);
        assertThat(i10.dentroDePoligonoDeter()).isTrue();
        assertThat(i10.menorDistanciaDeterKm()).isZero();
        assertThat(analise.inpe(d, 3).alertasDeterNoRaio()).isEqualTo(1);
    }

    @Test
    void usaCentroideDaAreaQuandoNaoHaCoordenada() {
        var d = demanda(null, null, quadrado(LON - 0.01, LAT - 0.01, 0.02));
        assertThat(AnaliseEspacial.ponto(d).origem()).isEqualTo("AREA");
        assertThat(AnaliseEspacial.ponto(d).lat()).isBetween(LAT - 0.02, LAT + 0.02);
    }

    @Test
    void semCoordenadaNemAreaCaiNoMunicipio() {
        totaisDoMunicipio();
        var d = demanda(null, null, null);
        when(demandas.listar(eq(ator), any(), any())).thenReturn(new PageResponse<>(List.of(
                resumo(4L, "DEM-2026/0004", "Sinop"), resumo(9L, "DEM-2026/0009", "Sinop")), 0, 10, 2, 1));
        var e = analise.analisar(ator, d, null);
        assertThat(e.referencia()).isEqualTo("MUNICIPIO");
        assertThat(e.raioKm()).isEqualTo(10);
        assertThat(e.inpe().focosNoRaio()).isZero();
        assertThat(e.inpe().focosMunicipio()).isEqualTo(7);
        assertThat(e.demandasProximas()).extracting(SugestaoTriagem.Proxima::numero).containsExactly("DEM-2026/0009");
        assertThat(e.demandasProximas().get(0).distanciaKm()).isNull();
    }

    @Test
    void demandasProximasCruzamDivisaMunicipalEUsamEscopoDoUsuario() {
        when(demandas.proximas(eq(ator), eq(LAT), eq(LON), eq(5.0), eq(4L), anyInt())).thenReturn(List.of(
                new DemandaProxima(resumo(9L, "DEM-2026/0009", "Vizinho do Norte"), 3.456)));
        var p = analise.proximas(ator, demanda(BigDecimal.valueOf(LAT), BigDecimal.valueOf(LON), null), 5);
        assertThat(p).hasSize(1);
        assertThat(p.get(0).municipio()).isEqualTo("Vizinho do Norte");
        assertThat(p.get(0).distanciaKm()).isEqualTo(3.46);
    }

    @Test
    void raioPadraoELimites() {
        assertThat(AnaliseEspacial.raio(null)).isEqualTo(10);
        assertThat(AnaliseEspacial.raio(-3.0)).isEqualTo(10);
        assertThat(AnaliseEspacial.raio(5.0)).isEqualTo(5);
        assertThat(AnaliseEspacial.raio(900.0)).isEqualTo(50);
    }

    @Test
    @SuppressWarnings("unchecked")
    void falhaNaConsultaDeDocumentosNaoQuebra() {
        var d = demanda(BigDecimal.valueOf(LAT), BigDecimal.valueOf(LON), null);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("x"));
        assertThat(analise.documentos(d, List.of())).isEmpty();
    }

    private static Map<String, Object> quadrado(double lon, double lat, double lado) {
        return Map.of("type", "Polygon", "coordinates", List.of(List.of(List.of(lon, lat), List.of(lon + lado, lat),
                List.of(lon + lado, lat + lado), List.of(lon, lat + lado), List.of(lon, lat))));
    }

    private static String json(Map<String, Object> g) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(g);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
