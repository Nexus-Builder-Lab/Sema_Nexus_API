package br.com.nexusbuilderlab.semanexus.intelligence.camada;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.Importacao;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaRepository.Feicao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CamadaPublicaServiceTest {

    CamadaPublicaRepository repo = mock(CamadaPublicaRepository.class);
    CamadaPublicaService service = new CamadaPublicaService(repo);

    static List<Importacao> importacoes() {
        return List.of(
                new Importacao(1L, TipoCamadaPublica.UNIDADE_CONSERVACAO, "MMA - CNUC", LocalDate.of(2025, 8, 1),
                        LocalDateTime.of(2026, 11, 25, 10, 0), 98, "https://dados.mma.gov.br", "CC BY", "ucs_mt.geojson.gz"),
                new Importacao(2L, TipoCamadaPublica.TERRA_INDIGENA, "FUNAI - CGGEO", LocalDate.of(2026, 11, 10),
                        LocalDateTime.of(2026, 11, 25, 10, 1), 75, "https://geoserver.funai.gov.br", "FUNAI/CGGEO",
                        "terras_indigenas_mt.geojson.gz"),
                new Importacao(3L, TipoCamadaPublica.HIDROGRAFIA, "ANA - SNIRH", LocalDate.of(2026, 11, 25),
                        LocalDateTime.of(2026, 11, 25, 10, 2), 5023, "https://www.snirh.gov.br", "Dados abertos ANA",
                        "hidrografia_mt.geojson.gz"));
    }

    @Test
    void metadadosRetornaOficialSemaFalsoFontesELicencas() {
        when(repo.importacoes()).thenReturn(importacoes());

        var m = service.metadados();

        assertThat(m.oficialSema()).isFalse();
        assertThat(m.atualizadoEm()).isEqualTo(LocalDateTime.of(2026, 11, 25, 10, 2));
        assertThat(m.aviso()).contains("Não são dados oficiais da SEMA-MT");
        assertThat(m.importacoes()).hasSize(3);
        assertThat(m.importacoes()).extracting(Importacao::licenca)
                .containsExactly("CC BY", "FUNAI/CGGEO", "Dados abertos ANA");
    }

    @Test
    void geojsonMontaFeatureCollectionEValidaFiltros() {
        when(repo.importacoes()).thenReturn(importacoes());
        when(repo.feicoes(TipoCamadaPublica.UNIDADE_CONSERVACAO, "Estadual", null, null, 5001))
                .thenReturn(List.of(new Feicao(11L, 1L, TipoCamadaPublica.UNIDADE_CONSERVACAO, "0000.51.0477",
                        "Parque Estadual do Guirá", "Parque", "Estadual", new BigDecimal("100.00"),
                        "{\"grupo\":\"Proteção Integral\"}",
                        "{\"type\":\"Polygon\",\"coordinates\":[[[-57.5,-17.8],[-57.4,-17.8],[-57.4,-17.7],[-57.5,-17.8]]]}")));

        var resp = service.geojson(TipoCamadaPublica.UNIDADE_CONSERVACAO, "Estadual", null, null, null);

        assertThat(resp.oficialSema()).isFalse();
        assertThat(resp.fonte()).isEqualTo("MMA - CNUC");
        assertThat(resp.licenca()).isEqualTo("CC BY");
        assertThat(resp.total()).isEqualTo(1);
        assertThat(resp.truncado()).isFalse();
        assertThat(resp.colecao().type()).isEqualTo("FeatureCollection");
        var ft = resp.colecao().features().get(0);
        assertThat(ft.properties()).containsEntry("oficialSema", false).containsEntry("grupo", "Proteção Integral");
        assertThat(ft.geometry()).containsEntry("type", "Polygon");

        assertThatThrownBy(() -> service.geojson(TipoCamadaPublica.UNIDADE_CONSERVACAO, null, 1, null, null))
                .isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(() -> service.geojson(TipoCamadaPublica.HIDROGRAFIA, null, 4, null, null))
                .isInstanceOf(DadosInvalidosException.class);
    }

    @Test
    void sobreposicaoCalculaDentroDeUcTiEProximidadeDeRioDeduplicada() {
        when(repo.importacoes()).thenReturn(importacoes());
        // Ponto (-15.55, -56.05) está dentro da UC, a ~2.2 km a oeste da TI e a ~1.1 km do Rio Cuiabá
        when(repo.naCaixa(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of(
                new Feicao(1L, 1L, TipoCamadaPublica.UNIDADE_CONSERVACAO, "UC-1", "Parque Nacional Teste",
                        "Parque", "Federal", new BigDecimal("5000.00"), null,
                        "{\"type\":\"Polygon\",\"coordinates\":[[[-56.10,-15.60],[-56.00,-15.60],[-56.00,-15.50],[-56.10,-15.50],[-56.10,-15.60]]]}"),
                new Feicao(2L, 2L, TipoCamadaPublica.TERRA_INDIGENA, "TI-1", "Terra Indígena Teste",
                        "Regularizada", "Federal (FUNAI)", new BigDecimal("12000.00"), null,
                        "{\"type\":\"Polygon\",\"coordinates\":[[[-56.03,-15.60],[-55.95,-15.60],[-55.95,-15.50],[-56.03,-15.50],[-56.03,-15.60]]]}"),
                new Feicao(3L, 3L, TipoCamadaPublica.HIDROGRAFIA, "46", "Rio Cuiabá",
                        "Curso d'água", "Nível 2", null, null,
                        "{\"type\":\"LineString\",\"coordinates\":[[-56.05,-15.56],[-56.04,-15.56]]}"),
                new Feicao(4L, 3L, TipoCamadaPublica.HIDROGRAFIA, "46", "Rio Cuiabá",
                        "Curso d'água", "Nível 2", null, null,
                        "{\"type\":\"LineString\",\"coordinates\":[[-56.05,-15.58],[-56.04,-15.58]]}"),
                new Feicao(5L, 1L, TipoCamadaPublica.UNIDADE_CONSERVACAO, "UC-2", "Parque Distante",
                        "Parque", "Estadual", new BigDecimal("100.00"), null,
                        "{\"type\":\"Polygon\",\"coordinates\":[[[-55.00,-15.00],[-54.90,-15.00],[-54.90,-14.90],[-55.00,-15.00]]]}")));

        var sob = service.sobreposicaoPonto(-15.55, -56.05, 10.0);

        assertThat(sob.oficialSema()).isFalse();
        assertThat(sob.referencia()).isEqualTo("COORDENADA");
        assertThat(sob.resumo().dentroUc()).isTrue();
        assertThat(sob.resumo().dentroTerraIndigena()).isFalse();
        assertThat(sob.unidadesConservacao()).hasSize(1);
        assertThat(sob.unidadesConservacao().get(0).dentro()).isTrue();
        assertThat(sob.unidadesConservacao().get(0).distanciaKm()).isZero();
        assertThat(sob.unidadesConservacao().get(0).licenca()).isEqualTo("CC BY");
        assertThat(sob.unidadesConservacao().get(0).oficialSema()).isFalse();
        assertThat(sob.terrasIndigenas()).hasSize(1);
        assertThat(sob.terrasIndigenas().get(0).distanciaKm()).isBetween(1.8, 2.5);
        // Dois trechos do mesmo Rio Cuiabá (Nível 2) são consolidados no mais próximo
        assertThat(sob.hidrografia()).hasSize(1);
        assertThat(sob.hidrografia().get(0).distanciaKm()).isBetween(0.9, 1.3);
        assertThat(sob.resumo().fontes()).hasSize(3);
    }

    @Test
    void sobreposicaoPorAreaDaDemandaDetectaCruzamentoComRioESemCoordenada() {
        when(repo.importacoes()).thenReturn(importacoes());
        when(repo.naCaixa(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of(
                new Feicao(10L, 3L, TipoCamadaPublica.HIDROGRAFIA, "6", "Rio Araguaia",
                        "Curso d'água", "Nível 1", null, null,
                        "{\"type\":\"LineString\",\"coordinates\":[[-56.20,-15.55],[-55.90,-15.55]]}")));

        Map<String, Object> area = Map.of("type", "Polygon", "coordinates", List.of(List.of(
                List.of(-56.10, -15.60), List.of(-56.00, -15.60), List.of(-56.00, -15.50), List.of(-56.10, -15.50),
                List.of(-56.10, -15.60))));
        DemandaDetalhe comArea = new DemandaDetalhe(1L, "DEM-2026/0001", "Teste", "Relato", Origem.DENUNCIA, null,
                new MunicipioRef(5103403, "Cuiabá", Bioma.CERRADO), null, null, null, BigDecimal.TEN, area,
                LocalDate.now(), null, null, null, StatusDemanda.PENDENTE, false, null, null, null, null, null, null,
                null, 0, null, null, null);

        var sobArea = service.sobreposicao(comArea, 5.0);
        assertThat(sobArea.referencia()).isEqualTo("AREA");
        assertThat(sobArea.hidrografia()).hasSize(1);
        assertThat(sobArea.hidrografia().get(0).dentro()).isTrue();
        assertThat(sobArea.hidrografia().get(0).distanciaKm()).isZero();

        DemandaDetalhe semCoord = new DemandaDetalhe(2L, "DEM-2026/0002", "Teste", "Relato", Origem.DENUNCIA, null,
                new MunicipioRef(5103403, "Cuiabá", Bioma.CERRADO), null, null, null, null, null,
                LocalDate.now(), null, null, null, StatusDemanda.PENDENTE, false, null, null, null, null, null, null,
                null, 0, null, null, null);
        var sobSem = service.sobreposicao(semCoord, 10.0);
        assertThat(sobSem.referencia()).isEqualTo("SEM_COORDENADA");
        assertThat(sobSem.oficialSema()).isFalse();
        assertThat(sobSem.unidadesConservacao()).isEmpty();
        assertThat(sobSem.resumo().fontes()).hasSize(3);
    }
}
