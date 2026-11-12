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
                new BigDecimal("-56.1"), 5103403, "Cuiabá", "CERRADO", "AQUA", null, null);
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
}
