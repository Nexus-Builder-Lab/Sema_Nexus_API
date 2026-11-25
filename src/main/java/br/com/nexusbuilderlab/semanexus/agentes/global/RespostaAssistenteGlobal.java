package br.com.nexusbuilderlab.semanexus.agentes.global;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Resposta do Assistente Global (Issue #139): texto curto e links montados pela API a partir do que as ferramentas
 * devolveram (nunca ids escritos pelo modelo).
 */
@Schema(description = "Resposta do Assistente Global (Ctrl+K). Nada é gravado: o assistente só consulta.")
public record RespostaAssistenteGlobal(
        @Schema(example = "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS") String aviso,
        @Schema(description = "LOCAL, CLAUDE ou DEMONSTRATIVO (regras locais, sem IA generativa)",
                example = "DEMONSTRATIVO") String modo,
        @Schema(description = "Modelo usado; nulo no modo demonstrativo", nullable = true) String modelo,
        @Schema(description = "Resposta curta em texto simples (sem links nem Markdown)") String resposta,
        @Schema(description = "Atalhos montados pela API a partir dos resultados das ferramentas")
        List<LinkAssistente> links,
        @Schema(description = "Ferramentas consultadas (somente leitura)",
                example = "[\"prazos_criticos\"]") List<String> ferramentas,
        @Schema(description = "Resumo do que cada ferramenta devolveu") List<String> evidencias,
        @Schema(description = "Avisos (ex.: resposta da IA descartada na conferência de números)")
        List<String> avisos) {

    /** Atalho para uma tela do Web. {@code tipo}: DEMANDA, DOCUMENTO ou TELA (tela filtrada). */
    @Schema(description = "Atalho para uma tela do Web (caminho relativo, montado pela API)")
    public record LinkAssistente(@Schema(example = "DEMANDA") String tipo,
                                 @Schema(example = "DEM-2026/0010 — Supressão de vegetação") String rotulo,
                                 @Schema(example = "/flux/demandas/10") String url) {
    }

    /** Pedido: a pergunta em linguagem natural. */
    @Schema(description = "Pergunta ao Assistente Global")
    public record Pergunta(@NotBlank @Size(max = AgenteGlobalService.MAX_PERGUNTA)
                           @Schema(example = "Quais demandas minhas estão com prazo vencido?") String pergunta) {
    }
}
