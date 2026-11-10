package br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto;

import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import io.swagger.v3.oas.annotations.media.Schema;

/** Contagem de demandas por município. */
@Schema(description = "Contagem de demandas por município de MT")
public record ContagemMunicipio(
        @Schema(description = "Código IBGE", example = "5103403") Integer municipioId,
        @Schema(example = "Cuiabá") String municipio,
        @Schema(example = "CERRADO") Bioma bioma,
        @Schema(example = "8") long total) {
}
