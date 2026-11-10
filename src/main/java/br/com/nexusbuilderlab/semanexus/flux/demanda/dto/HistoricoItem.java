package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** Evento do histórico da demanda (linha do tempo), do mais recente para o mais antigo. */
@Schema(description = "Evento do histórico da demanda")
public record HistoricoItem(
        @Schema(example = "21") Long id,
        TipoHistorico tipo,
        @Schema(description = "Status antes do evento (quando houve mudança)", nullable = true) StatusDemanda statusAnterior,
        @Schema(description = "Status depois do evento (quando houve mudança)", nullable = true) StatusDemanda statusNovo,
        @Schema(description = "Justificativa, observação ou descrição da atividade", nullable = true) String texto,
        UsuarioRef usuario,
        LocalDateTime data) {
}
