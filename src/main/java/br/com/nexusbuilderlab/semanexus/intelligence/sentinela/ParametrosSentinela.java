package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parâmetros da Sentinela Territorial (Issue #138), só por variáveis de ambiente (ver {@link SentinelaConfig}).
 *
 * @param agendamentoAtivo   {@code SENTINELA_AGENDAMENTO_ATIVO}: liga/desliga a rotina agendada (o endpoint continua)
 * @param cacheMinutos       {@code SENTINELA_CACHE_MINUTOS}: idade máxima do briefing servido antes de recalcular
 * @param janelaInpeDias     {@code SENTINELA_JANELA_INPE_DIAS}: janela recente dos focos/DETER (antes do fim do pacote)
 * @param minRegistrosInpe   {@code SENTINELA_MIN_REGISTROS_INPE}: focos + DETER na janela para virar anomalia
 * @param filaDias           {@code SENTINELA_FILA_DIAS}: dias parada no mesmo status para contar como acumulada
 * @param filaMinimo         {@code SENTINELA_FILA_MINIMO}: demandas paradas no status para virar anomalia
 * @param ativasPorFiscal    {@code SENTINELA_ATIVAS_POR_FISCAL}: limite de demandas ativas por fiscal da equipe
 * @param atrasadasPorFiscal {@code SENTINELA_ATRASADAS_POR_FISCAL}: limite de demandas atrasadas por fiscal da equipe
 * @param maxPorTipo         {@code SENTINELA_MAX_POR_TIPO}: máximo de anomalias listadas por tipo
 */
public record ParametrosSentinela(boolean agendamentoAtivo, int cacheMinutos, int janelaInpeDias, int minRegistrosInpe,
                                  int filaDias, int filaMinimo, double ativasPorFiscal, double atrasadasPorFiscal,
                                  int maxPorTipo) {

    public static final ParametrosSentinela PADRAO = new ParametrosSentinela(true, 10, 30, 10, 7, 3, 4.0, 1.0, 5);

    public ParametrosSentinela {
        cacheMinutos = Math.max(1, cacheMinutos);
        janelaInpeDias = Math.max(1, janelaInpeDias);
        minRegistrosInpe = Math.max(1, minRegistrosInpe);
        filaDias = Math.max(1, filaDias);
        filaMinimo = Math.max(1, filaMinimo);
        ativasPorFiscal = Math.max(0.5, ativasPorFiscal);
        atrasadasPorFiscal = Math.max(0.1, atrasadasPorFiscal);
        maxPorTipo = Math.max(1, Math.min(20, maxPorTipo));
    }

    /** Limites efetivos, devolvidos no briefing (transparência das regras). */
    public Map<String, Object> comoMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("janelaInpeDias", janelaInpeDias);
        m.put("minRegistrosInpe", minRegistrosInpe);
        m.put("filaDias", filaDias);
        m.put("filaMinimo", filaMinimo);
        m.put("ativasPorFiscal", ativasPorFiscal);
        m.put("atrasadasPorFiscal", atrasadasPorFiscal);
        m.put("cacheMinutos", cacheMinutos);
        return m;
    }
}
