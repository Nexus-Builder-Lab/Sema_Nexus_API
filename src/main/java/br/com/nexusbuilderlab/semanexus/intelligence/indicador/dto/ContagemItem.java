package br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Contagem de demandas por bioma, categoria ou situação (código e rótulo do catálogo correspondente). */
@Schema(description = "Contagem de demandas por item de catálogo (bioma, categoria ou situação)")
public record ContagemItem(
        @Schema(description = "Código do catálogo", example = "EM_ANDAMENTO") String codigo,
        @Schema(example = "Em andamento") String rotulo,
        @Schema(example = "22") long total) {
}
