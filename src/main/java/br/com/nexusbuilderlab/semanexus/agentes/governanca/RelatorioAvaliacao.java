package br.com.nexusbuilderlab.semanexus.agentes.governanca;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado da bateria determinística de avaliação dos agentes (Issue #131). Nada operacional é gravado. */
@Schema(description = "Bateria determinística de regressão/adversarial dos agentes (regras locais, sem modelo). "
        + "Nenhuma demanda, documento ou outro dado operacional é lido ou gravado; só a auditoria registra a execução.")
public record RelatorioAvaliacao(
        LocalDateTime executadaEm,
        @Schema(description = "Sempre DETERMINISTICO (sem chamada a modelo de IA)", example = "DETERMINISTICO")
        String modo,
        int total,
        int aprovados,
        int reprovados,
        long duracaoMs,
        List<CasosAvaliacao.Caso> casos) {
}
