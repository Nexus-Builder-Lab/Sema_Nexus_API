package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import br.com.nexusbuilderlab.semanexus.common.dto.EquipeRef;
import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Item da listagem de demandas. */
@Schema(description = "Demanda na listagem (dados fictícios na demonstração)")
public record DemandaResumo(
        @Schema(example = "10") Long id,
        @Schema(description = "Número DEM-AAAA/NNNN", example = "DEM-2026/0010") String numero,
        @Schema(example = "Supressão de vegetação nativa em área de reserva legal") String titulo,
        MunicipioRef municipio,
        Origem origem,
        @Schema(nullable = true) CategoriaFiscalizacao categoria,
        @Schema(nullable = true) Prioridade prioridade,
        StatusDemanda status,
        @Schema(example = "2026-11-10") LocalDate dataRecebimento,
        @Schema(description = "Prazo definido na distribuição", nullable = true) LocalDate prazo,
        @Schema(description = "Derivada: prazo < hoje e status ≠ FINALIZADA", example = "false") boolean atrasada,
        @Schema(nullable = true) EquipeRef equipe,
        @Schema(nullable = true) UsuarioRef fiscal) {
}
