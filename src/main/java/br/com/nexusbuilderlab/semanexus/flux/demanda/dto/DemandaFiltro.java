package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Filtros opcionais e combináveis da listagem de demandas (query string). */
public record DemandaFiltro(
        @Schema(description = "Texto livre: número (DEM-2026/0010) ou parte do título") String q,
        @Schema(description = "Situação no fluxo") StatusDemanda status,
        @Schema(description = "Código IBGE do município", example = "5103403") Integer municipioId,
        @Schema(description = "Categoria ambiental") CategoriaFiscalizacao categoria,
        @Schema(description = "Prioridade") Prioridade prioridade,
        @Schema(description = "Equipe designada") Long equipeId,
        @Schema(description = "Fiscal designado") Long fiscalId,
        @Schema(description = "Somente atrasadas (true) ou somente em dia (false): prazo < hoje e status ≠ FINALIZADA")
        Boolean atrasada,
        @Schema(description = "Recebidas a partir de (inclusive)", example = "2026-01-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
        @Schema(description = "Recebidas até (inclusive)", example = "2026-12-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
}
