package br.com.nexusbuilderlab.semanexus.docia.assistente.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Pergunta em linguagem natural sobre os documentos")
public record PerguntaRequest(@NotBlank @Size(max = 500) String pergunta) {
}
