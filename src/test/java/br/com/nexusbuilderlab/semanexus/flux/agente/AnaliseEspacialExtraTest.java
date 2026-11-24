package br.com.nexusbuilderlab.semanexus.flux.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.PorMunicipioResponse;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteRepository.PontoEspacial;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteService;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.TipoReferencia;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Janela de período, truncamento, leitura de geometria só dos candidatos, área e limites de documentos (Issue #132). */
class AnaliseEspacialExtraTest {

    private static final double LAT = -11.86;
    private static final double LON = -55.50;
    private final DemandaService demandas = mock(DemandaService.class);
    private final ReferenciaSateliteService ref = mock(ReferenciaSateliteService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AnaliseEspacial analise = new AnaliseEspacial(demandas, ref, jdbc, 90);

    private static DemandaDetalhe demanda(BigDecimal lat, BigDecimal lon, Map<String, Object> area, String localidade) {
        return new DemandaDetalhe(4L, "DEM-2026/0004", "Queimada", "Fogo", Origem.values()[0], null,
                new MunicipioRef(5107909, "Sinop", Bioma.AMAZONIA), localidade, lat, lon, null, area, LocalDate.now(),
                null, null, null, StatusDemanda.PENDENTE, false, null, null, null, null, null, null, null, 0, null, null,
                null);
    }

    private static DemandaDetalhe comPonto() {
        return demanda(BigDecimal.valueOf(LAT), BigDecimal.valueOf(LON), null, null);
    }

    private void focos(List<PontoEspacial> l) {
        when(ref.maisProximosNaCaixa(eq(TipoReferencia.FOCO_CALOR), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                any(), any(), anyDouble(), anyDouble(), anyInt())).thenReturn(l);
    }

    private void deter(List<PontoEspacial> l) {
        when(ref.maisProximosNaCaixa(eq(TipoReferencia.DESMATAMENTO), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), any(), any(), anyDouble(), anyDouble(), anyInt())).thenReturn(l);
    }

    private void totais() {
        when(ref.porMunicipio(any())).thenReturn(new PorMunicipioResponse("INPE", false, null, "", 7, List.of()));
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

    @Test
    void usaJanelaDe90DiasAteORecebimentoENaoLeGeometriaDeQuemEstaLonge() {
        totais();
        focos(List.of());
        deter(List.of(new PontoEspacial(7, LAT, LON + 0.45, null)));   // cerca de 49 km: além de raio + margem
        var i = analise.inpe(comPonto(), 5);
        assertThat(i.periodoDias()).isEqualTo(90);
        assertThat(i.alertasDeterNoRaio()).isZero();
        verify(ref, never()).geometria(7);
        var de = ArgumentCaptor.forClass(LocalDateTime.class);
        var ate = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(ref, atLeastOnce()).maisProximosNaCaixa(eq(TipoReferencia.FOCO_CALOR), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), de.capture(), ate.capture(), anyDouble(), anyDouble(), anyInt());
        assertThat(de.getValue()).isEqualTo(LocalDate.now().minusDays(90).atStartOfDay());
        assertThat(ate.getValue()).isEqualTo(LocalDate.now().plusDays(1).atStartOfDay());
    }

    @Test
    void marcaTruncadoQuandoOLimiteDeFocosEAtingidoDentroDoRaio() {
        totais();
        deter(List.of());
        List<PontoEspacial> muitos = new ArrayList<>();
        for (int k = 0; k < AnaliseEspacial.LIMITE_FOCOS; k++) {
            muitos.add(new PontoEspacial(k, LAT + 0.001, LON, null));
        }
        focos(muitos);
        var i = analise.inpe(comPonto(), 5);
        assertThat(i.truncado()).isTrue();
        assertThat(i.focosNoRaio()).isEqualTo(AnaliseEspacial.LIMITE_FOCOS);
        focos(List.of(new PontoEspacial(1, LAT + 0.001, LON, null)));
        assertThat(analise.inpe(comPonto(), 5).truncado()).isFalse();
    }

    @Test
    void areaMedeDaBordaEDetectaDeterQueContemAArea() {
        totais();
        focos(List.of(new PontoEspacial(1, LAT + 0.005, LON + 0.015, null)));   // cerca de 0,5 km da borda leste
        deter(List.of(new PontoEspacial(9, LAT, LON, null)));
        when(ref.geometria(9)).thenReturn(json(quadrado(LON - 0.05, LAT - 0.05, 0.2)));   // contem a area
        var i = analise.inpe(demanda(null, null, quadrado(LON, LAT, 0.01), null), 1);
        assertThat(i.focosNoRaio()).isEqualTo(1);
        assertThat(i.focoMaisProximoKm()).isLessThan(1.0);
        assertThat(i.dentroDePoligonoDeter()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void documentosLimitaCitamEFalhaDeUmaConsultaNaoDerrubaAsOutras() {
        List<Long> mil = new ArrayList<>();
        for (long k = 0; k < 1500; k++) {
            mil.add(k);
        }
        when(jdbc.queryForList(anyString(), eq(Long.class), any(), any(), any(), any())).thenReturn(mil);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("x"));
        var d = demanda(null, null, null, "Fazenda Boa Vista");
        assertThat(analise.documentos(d, List.of())).isEmpty();
        var args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, atLeast(2)).query(anyString(), any(RowMapper.class), args.capture());
        assertThat(args.getAllValues().get(0)).hasSize(1 + AnaliseEspacial.LIMITE_CITAM + 1);
    }
}
