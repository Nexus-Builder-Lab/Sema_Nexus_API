package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** Indicadores de produtividade do Flux (Issue #84). Dados fictícios: {@code demonstrativo} é sempre true. */
@Schema(description = "KPIs e agregações de produtividade (recorte: data de recebimento no período, escopo do perfil)")
public record ProdutividadeResposta(
        Kpis kpis,
        List<SetorSituacao> porSetorSituacao,
        List<SetorTempoMedio> tempoMedioPorSetor,
        List<MesHistorico> historicoMensal,
        List<Responsavel> porResponsavel,
        boolean demonstrativo) {

    @Schema(description = "Percentuais em 0-100 e tempos em dias, com 1 casa decimal; null sem base de cálculo")
    public record Kpis(long demandasNoPeriodo, long concluidas, BigDecimal percentualConclusao,
                       BigDecimal tempoMedioConclusaoDias, BigDecimal percentualConcluidasNoPrazo,
                       long atrasadasAtivas) {
    }

    @Schema(description = "setorId nulo = demandas ainda sem equipe/setor")
    public record SetorSituacao(Long setorId, String setor, long total, long concluidas, long ativas,
                                long atrasadas) {
    }

    public record SetorTempoMedio(Long setorId, String setor, BigDecimal tempoMedioDias) {
    }

    @Schema(description = "mes no formato AAAA-MM")
    public record MesHistorico(String mes, long recebidas, long concluidas) {
    }

    public record Responsavel(Long fiscalId, String fiscal, long total, long concluidas, long ativas,
                              long atrasadas, BigDecimal tempoMedioDias, BigDecimal percentualConclusao) {
    }
}
