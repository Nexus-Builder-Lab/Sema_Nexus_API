package br.com.nexusbuilderlab.semanexus.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Referência resumida a uma demanda. */
@Schema(description = "Referência resumida a uma demanda")
public record DemandaRef(
        @Schema(example = "10") Long id,
        @Schema(description = "Número no formato DEM-AAAA/NNNN", example = "DEM-2026/0010") String numero,
        @Schema(example = "Supressão de vegetação nativa em área de reserva legal") String titulo) {
}
