package br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Filtros opcionais e combináveis dos indicadores e do mapa (aplicados sobre as demandas). */
public record IndicadorFiltro(
        @Schema(description = "Demandas recebidas a partir de (inclusive)", example = "2026-01-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
        @Schema(description = "Demandas recebidas até (inclusive)", example = "2026-12-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
        @Schema(description = "Código IBGE do município", example = "5103403") Integer municipioId,
        @Schema(description = "Bioma predominante do município da demanda") Bioma bioma,
        @Schema(description = "Categoria ambiental") CategoriaFiscalizacao categoria,
        @Schema(description = "Situação no fluxo") StatusDemanda status) {
}
