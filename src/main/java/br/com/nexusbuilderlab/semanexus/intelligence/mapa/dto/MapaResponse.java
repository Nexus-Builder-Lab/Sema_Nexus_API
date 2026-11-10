package br.com.nexusbuilderlab.semanexus.intelligence.mapa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Pontos do mapa de Mato Grosso (a malha GeoJSON do IBGE é servida pelo Web, sem depender de internet). */
@Schema(description = "Pontos do mapa de MT com o marcador de dados simulados")
public record MapaResponse(
        @Schema(description = "Sempre true: pontos de demandas fictícias", example = "true") boolean demonstrativo,
        @Schema(example = "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS") String aviso,
        List<PontoMapa> pontos) {
}
