package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.core.notificacao.NotificacaoService;
import br.com.nexusbuilderlab.semanexus.core.notificacao.TipoNotificacao;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificação diária de prazos (Issue #25): avisa o fiscal designado quando o prazo vence em até
 * {@value #DIAS_ANTECEDENCIA} dias e, quando vencido, avisa o fiscal e os gestores. A chave inclui a data,
 * então reexecuções no mesmo dia não duplicam notificações.
 */
@Service
public class PrazoNotificacaoService {

    static final int DIAS_ANTECEDENCIA = 3;
    private static final Logger LOG = LoggerFactory.getLogger(PrazoNotificacaoService.class);

    private final DemandaRepository demandas;
    private final NotificacaoService notificacoes;
    private final Clock clock;

    public PrazoNotificacaoService(DemandaRepository demandas, NotificacaoService notificacoes, Clock clock) {
        this.demandas = demandas;
        this.notificacoes = notificacoes;
        this.clock = clock;
    }

    /** Execução agendada (padrão: todo dia às 07:00, horário de Cuiabá); configurável por {@code sema.notificacoes.cron}. */
    @Scheduled(cron = "${sema.notificacoes.cron:0 0 7 * * *}", zone = "America/Cuiaba")
    public void executarAgendado() {
        try {
            int criadas = verificar();
            LOG.info("Verificação diária de prazos concluída: {} notificação(ões) criada(s).", criadas);
        } catch (RuntimeException e) {
            LOG.error("Falha na verificação diária de prazos", e);
        }
    }

    /** Gera as notificações de prazo próximo e de atraso; devolve quantas foram criadas. */
    @Transactional
    public int verificar() {
        LocalDate hoje = LocalDate.now(clock.withZone(DemandaService.FUSO));
        int criadas = 0;

        for (Demanda d : demandas.buscarComPrazoEntre(hoje, hoje.plusDays(DIAS_ANTECEDENCIA))) {
            long dias = ChronoUnit.DAYS.between(hoje, d.getPrazo());
            String quando = dias == 0 ? "hoje" : dias == 1 ? "amanhã" : "em " + dias + " dias";
            boolean criada = notificacoes.notificar(d.getFiscal().getId(), TipoNotificacao.PRAZO_PROXIMO,
                    "Prazo próximo: " + d.getNumero(),
                    "A demanda " + d.getNumero() + " - " + d.getTitulo() + " vence " + quando + " ("
                            + d.getPrazo() + ").",
                    d.getId(), "PRAZO:" + d.getId() + ":" + hoje);
            criadas += criada ? 1 : 0;
        }

        for (Demanda d : demandas.buscarAtrasadas(hoje)) {
            long atraso = ChronoUnit.DAYS.between(d.getPrazo(), hoje);
            String titulo = "Demanda atrasada: " + d.getNumero();
            String msg = "A demanda " + d.getNumero() + " - " + d.getTitulo() + " está atrasada há " + atraso
                    + (atraso == 1 ? " dia" : " dias") + " (prazo " + d.getPrazo() + ").";
            String chave = "ATRASO:" + d.getId() + ":" + hoje;
            criadas += notificacoes.notificar(d.getFiscal().getId(), TipoNotificacao.DEMANDA_ATRASADA, titulo, msg,
                    d.getId(), chave) ? 1 : 0;
            criadas += notificacoes.notificarGestores(TipoNotificacao.DEMANDA_ATRASADA, titulo, msg, d.getId(),
                    chave, null);
        }
        return criadas;
    }
}
