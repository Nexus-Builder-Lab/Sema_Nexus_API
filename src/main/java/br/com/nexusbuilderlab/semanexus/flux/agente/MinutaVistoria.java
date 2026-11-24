package br.com.nexusbuilderlab.semanexus.flux.agente;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Minuta de relatório/parecer de vistoria gerada pelo Agente Copiloto de Minuta (Issue #137). Nada é gravado: o texto
 * é revisado e editado por uma pessoa e, se ela quiser, enviado como documento pelo upload existente.
 */
@Schema(description = "Minuta de relatório de vistoria (rascunho para revisão humana; nada é gravado pela API)")
public record MinutaVistoria(
        @Schema(description = "Aviso obrigatório, também na primeira linha da minuta",
                example = AgenteMinutaService.AVISO) String aviso,
        @Schema(description = "LOCAL (IA local), CLAUDE ou DEMONSTRATIVO (template determinístico)",
                example = "DEMONSTRATIVO") String modo,
        @Schema(description = "Modelo usado (nulo no modo DEMONSTRATIVO)", nullable = true,
                example = "qwen3:4b-instruct") String modelo,
        @Schema(example = "4") Long demandaId,
        @Schema(example = "DEM-2026/0004") String numero,
        @Schema(description = "Nome sugerido para o arquivo .md ao anexar a minuta revisada",
                example = "minuta-vistoria-DEM-2026-0004.md") String nomeArquivoSugerido,
        @Schema(description = "Minuta em Markdown com as seções: identificação, histórico, constatações da "
                + "fiscalização, documentos considerados, contexto INPE e conclusões/recomendações preliminares")
        String markdown,
        @Schema(description = "O que o agente consultou (ferramentas somente leitura)") List<String> evidencias,
        @Schema(description = "Avisos (ex.: IA indisponível, número não conferido, sem registro de fiscalização)")
        List<String> avisos) {
}
