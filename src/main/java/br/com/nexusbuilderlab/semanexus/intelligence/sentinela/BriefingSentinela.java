package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** Briefing executivo da Sentinela Territorial (Issue #138). Só leitura: nada é gravado a partir dele. */
@Schema(description = "Briefing executivo da Sentinela Territorial: anomalias detectadas por regras determinísticas e "
        + "resumo (IA local/Claude ou template, identificado em `modo`)")
public record BriefingSentinela(
        @Schema(example = "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS (demandas e equipes simuladas; focos e DETER são "
                + "dados públicos do INPE)") String aviso,
        LocalDateTime geradoEm,
        @Schema(description = "Momento até o qual o briefing é servido do cache antes de ser recalculado")
        LocalDateTime validoAte,
        @Schema(description = "AGENDADA (rotina diária) ou SOB_DEMANDA (cache vencido na consulta)",
                example = "AGENDADA") String origem,
        @Schema(description = "LOCAL, CLAUDE ou DEMONSTRATIVO (template determinístico)", example = "LOCAL")
        String modo,
        @Schema(description = "Modelo usado no resumo; nulo no modo DEMONSTRATIVO", example = "qwen3:4b-instruct")
        String modelo,
        @Schema(description = "Resumo executivo; os números citados conferem com os dados calculados") String resumo,
        ReferenciaInpe referenciaInpe,
        Indicadores indicadores,
        Totais totais,
        List<Anomalia> anomalias,
        @Schema(description = "Limites efetivos das regras (variáveis SENTINELA_*)") Map<String, Object> parametros,
        @Schema(description = "Consultas feitas para montar o briefing") List<String> evidencias,
        List<String> avisos) {

    /**
     * Janelas do INPE. O pacote do INPE é estático: o "agora" de cada tipo é a data/hora mais recente do pacote
     * (não a data de hoje), e a janela vai de {@code ate − janelaDias} até {@code ate}.
     */
    @Schema(description = "Janelas recentes do INPE, relativas ao fim de cada pacote (o pacote é estático)")
    public record ReferenciaInpe(@Schema(example = "INPE — dados públicos") String fonte, int janelaDias,
                                 LocalDate focosDe, LocalDate focosAte, LocalDate deterDe, LocalDate deterAte,
                                 String nota) {
    }

    @Schema(description = "Indicadores operacionais usados no briefing")
    public record Indicadores(@Schema(description = "Demandas não finalizadas") long demandasAbertas,
                              @Schema(description = "Demandas não finalizadas por status") Map<String, Long> porStatus,
                              @Schema(description = "Focos de calor na janela recente") long focosJanela,
                              @Schema(description = "Alertas DETER na janela recente") long deterJanela) {
    }

    @Schema(description = "Contagem das anomalias por severidade")
    public record Totais(long anomalias, long alta, long media, long baixa) {
    }

    @Schema(description = "Anomalia detectada por regra determinística")
    public record Anomalia(
            @Schema(example = "INPE_SEM_DEMANDA:5107909") String id,
            @Schema(description = "INPE_SEM_DEMANDA, FILA_ACUMULADA ou EQUIPE_SOBRECARREGADA",
                    example = "INPE_SEM_DEMANDA") String tipo,
            @Schema(description = "ALTA, MEDIA ou BAIXA", example = "ALTA") String severidade,
            String titulo,
            String descricao,
            @Schema(description = "Números que sustentam a anomalia") Map<String, Object> numeros,
            List<AcaoSugerida> acoes,
            @Schema(description = "Sujeito da anomalia: município, equipe ou status (rótulo)", example = "Sinop")
            String assunto) {
    }

    /**
     * Ação sugerida — só navegação: o Web abre o mapa/lista filtrados ou o cadastro pré-preenchido; nada é criado sem
     * uma pessoa confirmar no endpoint normal.
     */
    @Schema(description = "Ação sugerida (atalho de navegação; nada é gravado sem confirmação humana)")
    public record AcaoSugerida(
            @Schema(description = "FILTRAR_MAPA, FILTRAR_DEMANDAS ou CRIAR_DEMANDA_MONITORAMENTO",
                    example = "CRIAR_DEMANDA_MONITORAMENTO") String tipo,
            String rotulo,
            @Schema(description = "Parâmetros do filtro ou do cadastro pré-preenchido") Map<String, Object> parametros) {
    }
}
