package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração da Sentinela Territorial (Issue #138), só por variáveis de ambiente com padrões seguros. O horário da
 * rotina agendada fica em {@code SENTINELA_CRON} (padrão {@code 0 0 7 * * *}, America/Cuiaba), lido pelo
 * {@link SentinelaAgendador}.
 */
@Configuration
public class SentinelaConfig {

    @Bean
    ParametrosSentinela parametrosSentinela(
            @Value("${SENTINELA_AGENDAMENTO_ATIVO:true}") boolean agendamentoAtivo,
            @Value("${SENTINELA_CACHE_MINUTOS:10}") int cacheMinutos,
            @Value("${SENTINELA_JANELA_INPE_DIAS:30}") int janelaInpeDias,
            @Value("${SENTINELA_MIN_REGISTROS_INPE:10}") int minRegistrosInpe,
            @Value("${SENTINELA_FILA_DIAS:7}") int filaDias,
            @Value("${SENTINELA_FILA_MINIMO:3}") int filaMinimo,
            @Value("${SENTINELA_ATIVAS_POR_FISCAL:4}") double ativasPorFiscal,
            @Value("${SENTINELA_ATRASADAS_POR_FISCAL:1}") double atrasadasPorFiscal,
            @Value("${SENTINELA_MAX_POR_TIPO:5}") int maxPorTipo) {
        return new ParametrosSentinela(agendamentoAtivo, cacheMinutos, janelaInpeDias, minRegistrosInpe, filaDias,
                filaMinimo, ativasPorFiscal, atrasadasPorFiscal, maxPorTipo);
    }
}
