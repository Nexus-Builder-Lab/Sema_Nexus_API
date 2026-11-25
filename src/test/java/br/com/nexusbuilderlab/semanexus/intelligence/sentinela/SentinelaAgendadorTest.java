package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/** Rotina agendada da Sentinela (Issue #138): desligável, cron por variável e nunca propaga falha. */
class SentinelaAgendadorTest {

    private final SentinelaTerritorialService service = mock(SentinelaTerritorialService.class);

    private static ParametrosSentinela parametros(boolean ativo) {
        ParametrosSentinela p = ParametrosSentinela.PADRAO;
        return new ParametrosSentinela(ativo, p.cacheMinutos(), p.janelaInpeDias(), p.minRegistrosInpe(), p.filaDias(),
                p.filaMinimo(), p.ativasPorFiscal(), p.atrasadasPorFiscal(), p.maxPorTipo());
    }

    @Test
    void desligadaNaoCalcula() {
        assertThat(new SentinelaAgendador(service, parametros(false)).executarAgendado()).isFalse();
        verify(service, never()).atualizarAgendado();
    }

    @Test
    void ligadaPreCalculaOBriefing() {
        when(service.atualizarAgendado()).thenReturn(new BriefingSentinela("aviso", LocalDateTime.now(),
                LocalDateTime.now(), "AGENDADA", "DEMONSTRATIVO", null, "resumo", null, null,
                new BriefingSentinela.Totais(0, 0, 0, 0), List.of(), Map.of(), List.of(), List.of()));
        assertThat(new SentinelaAgendador(service, parametros(true)).executarAgendado()).isTrue();
        verify(service).atualizarAgendado();
    }

    @Test
    void atualizacaoJaEmAndamentoNaoRecalcula() {
        when(service.atualizarAgendado()).thenReturn(null);
        assertThat(new SentinelaAgendador(service, parametros(true)).executarAgendado()).isFalse();
    }

    @Test
    void falhaNaoPropaga() {
        when(service.atualizarAgendado()).thenThrow(new IllegalStateException("banco fora"));
        assertThat(new SentinelaAgendador(service, parametros(true)).executarAgendado()).isFalse();
    }

    @Test
    void cronConfiguravelPadraoSeteHorasDeCuiaba() throws Exception {
        Method m = SentinelaAgendador.class.getMethod("executarAgendado");
        Scheduled s = m.getAnnotation(Scheduled.class);
        assertThat(s.cron()).isEqualTo("${SENTINELA_CRON:0 0 7 * * *}");
        assertThat(s.zone()).isEqualTo("America/Cuiaba");
    }

    @Test
    void parametrosTemPisosSeguros() {
        ParametrosSentinela p = new ParametrosSentinela(true, 0, 0, 0, 0, 0, 0, 0, 999);
        assertThat(p.cacheMinutos()).isEqualTo(1);
        assertThat(p.janelaInpeDias()).isEqualTo(1);
        assertThat(p.maxPorTipo()).isEqualTo(20);
    }
}
