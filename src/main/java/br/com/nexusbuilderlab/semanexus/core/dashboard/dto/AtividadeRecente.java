package br.com.nexusbuilderlab.semanexus.core.dashboard.dto;

import br.com.nexusbuilderlab.semanexus.common.dto.DemandaRef;
import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** Evento recente nas demandas (alimenta o bloco "atividades recentes" do dashboard). */
@Schema(description = "Atividade recente nas demandas")
public record AtividadeRecente(
        LocalDateTime data,
        TipoHistorico tipo,
        @Schema(nullable = true) String texto,
        DemandaRef demanda,
        UsuarioRef usuario) {
}
