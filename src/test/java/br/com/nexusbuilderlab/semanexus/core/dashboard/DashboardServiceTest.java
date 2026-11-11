package br.com.nexusbuilderlab.semanexus.core.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaHistoricoRepository;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.IndicadorRepository;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.IndicadorService;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class DashboardServiceTest {

    @Test
    void montaDashboardDemonstrativoComDezAtividadesNoMaximo() {
        IndicadorRepository repo = mock(IndicadorRepository.class);
        DemandaHistoricoRepository historico = mock(DemandaHistoricoRepository.class);
        when(repo.demandas(any())).thenReturn(List.of());
        when(repo.contarFiscalizacoes(any())).thenReturn(0L);
        when(historico.findRecentes(any())).thenReturn(List.of());

        var r = new DashboardService(new IndicadorService(repo, Clock.systemUTC()), historico).dashboard();

        assertThat(r.demonstrativo()).isTrue();
        assertThat(r.aviso()).contains("DEMONSTRATIVO");
        assertThat(r.resumo().totalDemandas()).isZero();
        assertThat(r.porSituacao()).hasSize(7);
        assertThat(r.porBioma()).hasSize(3);
        assertThat(r.atividadesRecentes()).isEmpty();
        verify(historico).findRecentes(PageRequest.of(0, 10));
    }
}
