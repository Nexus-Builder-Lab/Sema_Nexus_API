package br.com.nexusbuilderlab.semanexus.common.dto;

import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import io.swagger.v3.oas.annotations.media.Schema;

/** Referência resumida a um município de MT. */
@Schema(description = "Referência resumida a um município de Mato Grosso")
public record MunicipioRef(
        @Schema(description = "Código IBGE", example = "5103403") Integer id,
        @Schema(example = "Cuiabá") String nome,
        @Schema(example = "CERRADO") Bioma bioma) {
}
