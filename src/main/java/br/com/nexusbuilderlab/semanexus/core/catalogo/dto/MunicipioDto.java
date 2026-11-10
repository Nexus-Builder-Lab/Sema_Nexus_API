package br.com.nexusbuilderlab.semanexus.core.catalogo.dto;

import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Município de Mato Grosso (referência geográfica pública do IBGE). */
@Schema(description = "Município de Mato Grosso (código IBGE) com bioma predominante e centroide")
public record MunicipioDto(
        @Schema(description = "Código IBGE", example = "5103403") Integer id,
        @Schema(description = "Nome", example = "Cuiabá") String nome,
        @Schema(description = "Região de planejamento", example = "Baixada Cuiabana") String regiao,
        @Schema(description = "Bioma predominante", example = "CERRADO") Bioma bioma,
        @Schema(description = "Latitude do centroide", example = "-15.601") BigDecimal latitude,
        @Schema(description = "Longitude do centroide", example = "-56.097") BigDecimal longitude) {

    public static MunicipioDto de(Municipio m) {
        return new MunicipioDto(m.getId(), m.getNome(), m.getRegiao(), m.getBioma(), m.getLatitude(), m.getLongitude());
    }
}
