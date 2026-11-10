package br.com.nexusbuilderlab.semanexus.docia.documento.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Vínculo de um documento existente a uma demanda. */
@Schema(description = "Vínculo de um documento já cadastrado a uma demanda")
public record VinculoDocumentoRequest(
        @Schema(description = "Documento existente (GET /api/documentos)", example = "7") @NotNull Long documentoId) {
}
