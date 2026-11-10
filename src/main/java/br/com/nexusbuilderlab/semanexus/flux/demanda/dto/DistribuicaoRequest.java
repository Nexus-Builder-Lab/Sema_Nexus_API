package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Distribuição (Aguardando distribuição → Distribuída): equipe, fiscal da equipe e prazo. */
@Schema(description = "Distribuição da demanda a uma equipe e a um fiscal membro dela")
public record DistribuicaoRequest(
        @Schema(description = "Equipe ativa (GET /api/equipes)", example = "1") @NotNull Long equipeId,
        @Schema(description = "Fiscal membro da equipe (GET /api/equipes/{id}/membros)", example = "3") @NotNull Long fiscalId,
        @Schema(description = "Prazo (>= hoje)", example = "2026-12-10") @NotNull @FutureOrPresent LocalDate prazo,
        @Schema(nullable = true) @Size(max = 2000) String observacoes) {
}
