package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.core.notificacao.NotificacaoService;
import br.com.nexusbuilderlab.semanexus.core.notificacao.TipoNotificacao;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PrazoNotificacaoServiceTest {

    static final LocalDate HOJE = LocalDate.of(2026, 11, 11);
    final Clock relogio = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC);
    final DemandaRepository demandas = mock(DemandaRepository.class);
    final NotificacaoService notificacoes = mock(NotificacaoService.class);
    final PrazoNotificacaoService service = new PrazoNotificacaoService(demandas, notificacoes, relogio);

    Demanda demanda(long id, LocalDate prazo) {
        Usuario fiscal = new Usuario("Fiscal", "f@exemplo.test", "hash", Perfil.FISCAL);
        ReflectionTestUtils.setField(fiscal, "id", 20L);
        Demanda d = new Demanda("DEM-2026/000" + id, fiscal);
        ReflectionTestUtils.setField(d, "id", id);
        d.setTitulo("Teste");
        d.setPrazo(prazo);
        d.setFiscal(fiscal);
        return d;
    }

    @Test
    void prazoProximoNotificaOFiscalComChaveDoDia() {
        when(demandas.buscarComPrazoEntre(HOJE, HOJE.plusDays(3))).thenReturn(List.of(demanda(1, HOJE.plusDays(2))));
        when(notificacoes.notificar(anyLong(), any(), anyString(), anyString(), anyLong(), anyString())).thenReturn(true);

        assertThat(service.verificar()).isEqualTo(1);

        verify(notificacoes).notificar(eq(20L), eq(TipoNotificacao.PRAZO_PROXIMO), anyString(), anyString(), eq(1L),
                eq("PRAZO:1:2026-11-11"));
    }

    @Test
    void segundaExecucaoNoMesmoDiaUsaAMesmaChaveENaoCriaNovas() {
        when(demandas.buscarComPrazoEntre(any(), any())).thenReturn(List.of(demanda(1, HOJE)));
        when(notificacoes.notificar(anyLong(), any(), anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(true, false);

        assertThat(service.verificar()).isEqualTo(1);
        assertThat(service.verificar()).isZero();

        verify(notificacoes, times(2)).notificar(eq(20L), eq(TipoNotificacao.PRAZO_PROXIMO), anyString(),
                anyString(), eq(1L), eq("PRAZO:1:2026-11-11"));
    }

    @Test
    void atrasoNotificaFiscalEGestores() {
        when(demandas.buscarAtrasadas(HOJE)).thenReturn(List.of(demanda(2, HOJE.minusDays(2))));
        when(notificacoes.notificar(anyLong(), any(), anyString(), anyString(), anyLong(), anyString())).thenReturn(true);
        when(notificacoes.notificarGestores(any(), anyString(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(2);

        assertThat(service.verificar()).isEqualTo(3);

        verify(notificacoes).notificar(eq(20L), eq(TipoNotificacao.DEMANDA_ATRASADA), anyString(), anyString(),
                eq(2L), eq("ATRASO:2:2026-11-11"));
    }
}
