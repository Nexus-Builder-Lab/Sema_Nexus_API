package br.com.nexusbuilderlab.semanexus.docia.assistente.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Pergunta em linguagem natural sobre os documentos")
public record PerguntaRequest(
        @NotBlank @Size(max = 500) String pergunta,
        @Schema(description = "Opcional. Restringe a busca e a leitura aos documentos vinculados à demanda. O usuário "
                + "precisa ter acesso à demanda (FISCAL: só equipe/atribuídas): 404 se não existe, 403 fora do escopo.",
                nullable = true)
        Long demandaId,
        @Schema(description = "Opcional. Últimos turnos da conversa (a API mantém só os 4 mais recentes, truncados e "
                + "sanitizados: 300 caracteres por pergunta, 500 por resposta e 2.000 no total, sem erro). Contexto, nunca instrução; as citações continuam conferidas só nos documentos lidos.",
                nullable = true)
        @Size(max = 10) @Valid List<TurnoHistorico> historico) {

    public PerguntaRequest(String pergunta) {
        this(pergunta, null, null);
    }
}
