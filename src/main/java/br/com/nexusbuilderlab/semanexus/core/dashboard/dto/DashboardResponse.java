package br.com.nexusbuilderlab.semanexus.core.dashboard.dto;

import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.ContagemItem;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.ResumoIndicadores;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Dados do dashboard inicial: totais, situação das demandas e atividades recentes. */
@Schema(description = "Dashboard inicial (dados simulados)")
public record DashboardResponse(
        @Schema(description = "Sempre true: dados fictícios", example = "true") boolean demonstrativo,
        @Schema(example = "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS") String aviso,
        ResumoIndicadores resumo,
        @Schema(description = "Demandas por situação, na ordem do fluxo") List<ContagemItem> porSituacao,
        @Schema(description = "Últimos eventos das demandas (até 10), do mais recente ao mais antigo")
        List<AtividadeRecente> atividadesRecentes) {
}
