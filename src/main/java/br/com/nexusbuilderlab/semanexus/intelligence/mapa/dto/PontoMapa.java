package br.com.nexusbuilderlab.semanexus.intelligence.mapa.dto;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Marcador do mapa: uma demanda posicionada pela coordenada informada ou, na falta dela, pelo centroide do município. */
@Schema(description = "Marcador do mapa (demanda fictícia)")
public record PontoMapa(
        @Schema(example = "10") Long demandaId,
        @Schema(example = "DEM-2026/0010") String numero,
        String titulo,
        @Schema(example = "5103403") Integer municipioId,
        @Schema(example = "Cuiabá") String municipio,
        @Schema(example = "-15.601") BigDecimal latitude,
        @Schema(example = "-56.097") BigDecimal longitude,
        @Schema(description = "true quando a demanda não tem coordenada própria e foi usado o centroide do município",
                example = "false") boolean coordenadaAproximada,
        StatusDemanda status,
        @Schema(nullable = true) CategoriaFiscalizacao categoria,
        @Schema(nullable = true) Prioridade prioridade,
        @Schema(description = "Derivada: prazo < hoje e status ≠ FINALIZADA", example = "false") boolean atrasada) {
}
