package br.com.nexusbuilderlab.semanexus.docia.assistente.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Um turno anterior da conversa. Entra no prompt apenas como contexto (dados), nunca como instrução; "
        + "é truncado e sanitizado pela API.")
public record TurnoHistorico(
        @Schema(description = "Pergunta feita no turno", example = "Qual a área desmatada em Colniza?")
        @Size(max = 1000) String pergunta,
        @Schema(description = "Resposta que o assistente deu no turno", example = "210 hectares, segundo o Relatório.")
        @Size(max = 4000) String resposta) {
}
