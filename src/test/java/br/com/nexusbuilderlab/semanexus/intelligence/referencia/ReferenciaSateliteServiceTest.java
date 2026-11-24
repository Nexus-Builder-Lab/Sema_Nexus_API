package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.ContagemMunicipio;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.Importacao;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.Ponto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenciaSateliteServiceTest {

    ReferenciaSateliteRepository repo = mock(ReferenciaSateliteRepository.class);
    ReferenciaSateliteService service = new ReferenciaSateliteService(repo);
    ReferenciaFiltro vazio = new ReferenciaFiltro(null, null, null, null, null);

    static Ponto ponto(long id) {
        return new Ponto(id, "FOCO_CALOR", LocalDateTime.parse("2026-10-01T12:00:00"), new BigDecimal("-15.6"),
                new BigDecimal("-56.1"), 5103403, "Cuiabá", "CERRADO", "AQUA", null, null, null);
    }

    static Importacao importacao(String importadoEm) {
        return new Importacao(1L, "INPE", "FOCO_CALOR", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-11-01"),
                LocalDateTime.parse(importadoEm), 10, "https://x", "CC BY-SA 4.0", "a.csv.gz");
    }

    @Test
    void metadadosTrazFonteOficialSemaFalsoEUltimaAtualizacao() {
        when(repo.importacoes()).thenReturn(List.of(importacao("2026-11-10T10:00:00"), importacao("2026-11-11T09:00:00")));

        var m = service.metadados();

        assertThat(m.fonte()).isEqualTo("INPE — dados públicos");
        assertThat(m.oficialSema()).isFalse();
        assertThat(m.atualizadoEm()).isEqualTo(LocalDateTime.parse("2026-11-11T09:00:00"));
        assertThat(m.importacoes()).hasSize(2);
    }

    @Test
    void pontosUsaLimitePadraoETruncaQuandoHaMais() {
        List<Ponto> muitos = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            muitos.add(ponto(i));
        }
        when(repo.pontos(any(), eq(6))).thenReturn(muitos);
        when(repo.importacoes()).thenReturn(List.of(importacao("2026-11-10T10:00:00")));

        var r = service.pontos(vazio, 5);

        assertThat(r.pontos()).hasSize(5);
        assertThat(r.truncado()).isTrue();
        assertThat(r.limite()).isEqualTo(5);
        assertThat(r.fonte()).isEqualTo("INPE — dados públicos");
        assertThat(r.oficialSema()).isFalse();
        assertThat(r.atualizadoEm()).isNotNull();
    }

    @Test
    void limiteAcimaDoMaximoEhLimitadoEPadraoEh5000() {
        when(repo.pontos(any(), eq(20001))).thenReturn(List.of());
        when(repo.pontos(any(), eq(5001))).thenReturn(List.of());

        assertThat(service.pontos(vazio, 999999).limite()).isEqualTo(20000);
        assertThat(service.pontos(vazio, null).limite()).isEqualTo(5000);
        verify(repo).pontos(any(), eq(20001));
    }

    @Test
    void periodoInvertidoEhRejeitado() {
        var f = new ReferenciaFiltro(null, LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-01"), null, null);
        assertThatThrownBy(() -> service.pontos(f, null)).isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(() -> service.porMunicipio(f)).isInstanceOf(DadosInvalidosException.class);
    }

    @Test
    void porMunicipioSomaOTotalEMarcaAFonte() {
        when(repo.porMunicipio(any())).thenReturn(List.of(
                new ContagemMunicipio(5103403, "Cuiabá", "CERRADO", 7), new ContagemMunicipio(5107909, "Sinop", "AMAZONIA", 3)));

        var r = service.porMunicipio(vazio);

        assertThat(r.total()).isEqualTo(10);
        assertThat(r.oficialSema()).isFalse();
        assertThat(r.fonte()).isEqualTo("INPE — dados públicos");
    }

    static final String POLIGONO = "{\"type\":\"MultiPolygon\",\"coordinates\":[[[[-55.52,-11.88],[-55.50,-11.88],"
            + "[-55.50,-11.86],[-55.52,-11.88]]]]}";

    static ReferenciaSateliteRepository.Registro registro(String tipo, String areaKm2, String atributos,
                                                          String geometria) {
        BigDecimal km2 = areaKm2 == null ? null : new BigDecimal(areaKm2);
        Ponto p = new Ponto(9L, tipo, LocalDateTime.parse("2026-10-09T00:00:00"), new BigDecimal("-11.872"),
                new BigDecimal("-55.512"), 5107909, "Sinop", "AMAZONIA", "AMAZONIA-1",
                "DESMATAMENTO".equals(tipo) ? "CICATRIZ_DE_QUEIMADA" : null, km2,
                br.com.nexusbuilderlab.semanexus.common.geo.GeoJson.km2ParaHectares(km2));
        return new ReferenciaSateliteRepository.Registro(p, atributos, geometria, importacao("2026-11-24T10:00:00"));
    }

    @Test
    void detalheDeterTrazPoligonoAreaEmHaECampoDaFonte() {
        when(repo.registro(9L)).thenReturn(java.util.Optional.of(registro("DESMATAMENTO", "0.1320",
                "{\"gid\":\"40570_curr\",\"areamunkm\":0.13200473633075194,\"areauckm\":0}", POLIGONO)));

        var d = service.detalhe(9L);

        assertThat(d.fonte()).isEqualTo("INPE — dados públicos");
        assertThat(d.oficialSema()).isFalse();
        assertThat(d.areaInformada()).isTrue();
        assertThat(d.ponto().areaKm2()).isEqualByComparingTo("0.1320");
        assertThat(d.ponto().areaHa()).isEqualByComparingTo("13.20");
        assertThat(d.areaCampoFonte()).isEqualTo("areamunkm");
        assertThat(d.areaKm2Fonte()).isEqualByComparingTo("0.13200473633075194");
        assertThat(d.areaObservacao()).contains("areamunkm", "1 km² = 100 ha");
        assertThat(d.geometria()).containsEntry("type", "MultiPolygon");
        assertThat(d.atributos()).containsEntry("gid", "40570_curr");
        assertThat(d.importacao().licenca()).isEqualTo("CC BY-SA 4.0");
    }

    @Test
    void detalheDeterDoCerradoUsaAreaDoRecorte() {
        when(repo.registro(9L)).thenReturn(java.util.Optional.of(registro("DESMATAMENTO", "0.0876",
                "{\"areatotalkm\":0.0876,\"areamunkm\":0.05}", POLIGONO)));

        assertThat(service.detalhe(9L).areaCampoFonte()).isEqualTo("areamunkm");
    }

    @Test
    void detalheDoFocoNaoTemAreaNemPoligonoEExplicaOMotivo() {
        when(repo.registro(9L)).thenReturn(java.util.Optional.of(registro("FOCO_CALOR", null,
                "{\"frp\":145.0,\"risco_fogo\":0.82,\"numero_dias_sem_chuva\":3}", POLIGONO)));

        var d = service.detalhe(9L);

        assertThat(d.areaInformada()).isFalse();
        assertThat(d.ponto().areaHa()).isNull();
        assertThat(d.areaCampoFonte()).isNull();
        assertThat(d.areaKm2Fonte()).isNull();
        assertThat(d.geometria()).as("foco nunca ganha polígono").isNull();
        assertThat(d.areaObservacao()).isEqualTo(ReferenciaDto.AREA_FOCO_NAO_INFORMADA)
                .contains("não informada pela fonte", "ponto");
        assertThat(d.atributos()).containsKeys("frp", "risco_fogo", "numero_dias_sem_chuva");
    }

    @Test
    void detalheUsaOValorCompletoDaFonteParaHectaresDeAreasMinusculas() {
        when(repo.registro(9L)).thenReturn(java.util.Optional.of(registro("DESMATAMENTO", "0.0000",
                "{\"areamunkm\":0.0000031257757833227515}", POLIGONO)));

        var d = service.detalhe(9L);

        assertThat(d.areaInformada()).isTrue();
        assertThat(d.areaKm2Fonte()).isEqualByComparingTo("0.0000031257757833227515");
        assertThat(d.ponto().areaHa()).isEqualByComparingTo("0.00");
    }

    @Test
    void detalheInexistenteDa404() {
        when(repo.registro(77L)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.detalhe(77L))
                .isInstanceOf(br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException.class);
    }
}
