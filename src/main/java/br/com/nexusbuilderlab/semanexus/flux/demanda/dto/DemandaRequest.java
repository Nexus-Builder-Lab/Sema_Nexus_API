package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Corpo de cadastro e de edição de demanda de fiscalização. O município precisa ser de MT e as
 * coordenadas (opcionais) ficam no retângulo de MT; o status inicial é sempre PENDENTE.
 */
@Schema(description = "Cadastro/edição de demanda de fiscalização ambiental (dados fictícios na demonstração)")
public record DemandaRequest(
        @Schema(description = "Título (5 a 200 caracteres)", example = "Supressão de vegetação nativa em área de reserva legal")
        @NotBlank @Size(min = 5, max = 200) String titulo,
        @Schema(description = "Descrição do fato a ser apurado", example = "Relato fictício de desmatamento em propriedade rural.")
        @NotBlank @Size(max = 4000) String descricao,
        @Schema(description = "Origem da demanda (catálogo /api/catalogos/origens)", example = "DENUNCIA")
        @NotNull Origem origem,
        @Schema(description = "Categoria ambiental; pode ser sugerida no cadastro e é confirmada na triagem",
                example = "DESMATAMENTO_ILEGAL", nullable = true)
        CategoriaFiscalizacao categoria,
        @Schema(description = "Código IBGE de um município de MT (catálogo /api/catalogos/municipios)", example = "5103403")
        @NotNull Integer municipioId,
        @Schema(description = "Localidade ou referência (fazenda, comunidade, rodovia...)", example = "Estrada vicinal km 12", nullable = true)
        @Size(max = 200) String localidade,
        @Schema(description = "Latitude (-18,1 a -7,3)", example = "-15.601", nullable = true)
        @DecimalMin("-18.1") @DecimalMax("-7.3") BigDecimal latitude,
        @Schema(description = "Longitude (-61,7 a -50,2)", example = "-56.097", nullable = true)
        @DecimalMin("-61.7") @DecimalMax("-50.2") BigDecimal longitude,
        @Schema(description = "Área afetada em hectares (> 0). Com areaGeometria, a API calcula a área e rejeita (400) "
                + "se a informada divergir mais de 2%; a área gravada é sempre a calculada", example = "42.35", nullable = true)
        @DecimalMin(value = "0.01", message = "A área deve ser maior que zero.") @DecimalMax("9999999999.99") BigDecimal areaHa,
        @Schema(description = "Polígono GeoJSON (Polygon/MultiPolygon, WGS84 [lon, lat]) da área afetada: anéis fechados, "
                + "vértices dentro de MT, até 2000 vértices e 200 KB. Sem latitude/longitude, a API preenche com o centroide",
                types = {"object", "null"})
        Map<String, Object> areaGeometria,
        @Schema(description = "Data de recebimento (não pode ser futura)", example = "2026-11-10")
        @NotNull @PastOrPresent LocalDate dataRecebimento,
        @Schema(description = "Prioridade sugerida; é confirmada na triagem", example = "ALTA", nullable = true)
        Prioridade prioridade,
        @Schema(description = "Prazo sugerido (>= data de recebimento); o prazo definitivo é fixado na distribuição",
                example = "2026-12-10", nullable = true)
        LocalDate prazoSugerido,
        @Schema(description = "Observações livres", nullable = true)
        @Size(max = 2000) String observacoes) {
}
