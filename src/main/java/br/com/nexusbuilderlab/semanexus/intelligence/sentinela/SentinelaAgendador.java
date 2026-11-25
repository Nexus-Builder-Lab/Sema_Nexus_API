package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotina agendada da Sentinela Territorial (Issue #138): pré-calcula o briefing e o deixa no cache. Horário em
 * {@code SENTINELA_CRON} (padrão todo dia às 07:00, America/Cuiaba; {@code -} desliga no Spring) e chave
 * {@code SENTINELA_AGENDAMENTO_ATIVO=false} para desligar sem mexer no cron. Desligada, o endpoint continua
 * funcionando (calcula sob demanda).
 */
@Component
public class SentinelaAgendador {

    private static final Logger LOG = LoggerFactory.getLogger(SentinelaAgendador.class);

    private final SentinelaTerritorialService service;
    private final ParametrosSentinela parametros;

    public SentinelaAgendador(SentinelaTerritorialService service, ParametrosSentinela parametros) {
        this.service = service;
        this.parametros = parametros;
    }

    /** Devolve true quando o briefing foi recalculado. Nunca propaga exceção (não derruba o agendador). */
    @Scheduled(cron = "${SENTINELA_CRON:0 0 7 * * *}", zone = "America/Cuiaba")
    public boolean executarAgendado() {
        if (!parametros.agendamentoAtivo()) {
            LOG.debug("Rotina da Sentinela Territorial desligada (SENTINELA_AGENDAMENTO_ATIVO=false).");
            return false;
        }
        try {
            BriefingSentinela b = service.atualizarAgendado();
            if (b == null) {
                LOG.info("Briefing da Sentinela já em atualização; rotina agendada ignorada nesta execução.");
                return false;
            }
            LOG.info("Briefing da Sentinela pré-calculado: {} anomalia(s), modo {}.", b.totais().anomalias(),
                    b.modo());
            return true;
        } catch (RuntimeException e) {
            LOG.error("Falha ao pré-calcular o briefing da Sentinela ({})", e.getClass().getSimpleName());
            return false;
        }
    }
}
