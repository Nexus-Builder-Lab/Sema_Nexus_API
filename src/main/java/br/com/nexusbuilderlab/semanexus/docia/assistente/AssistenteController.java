package br.com.nexusbuilderlab.semanexus.docia.assistente;

import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.PerguntaRequest;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.RespostaAssistente;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/docia")
@Tag(name = "DocIA — Assistente", description = "Perguntas sobre os documentos, com modo e citações")
public class AssistenteController {

    private final AssistenteService service;

    public AssistenteController(AssistenteService service) {
        this.service = service;
    }

    @Operation(summary = "Pergunta sobre os documentos",
            description = "Qualquer perfil autenticado. Resposta extrativa com `modo` e `referencias` (documento e "
                    + "trecho). Sem base nos documentos: 'não há informação suficiente'. Perguntas sobre legislação, "
                    + "multas ou procedimentos oficiais são recusadas. `demandaId` (opcional) restringe a busca e a leitura aos "
                    + "documentos vinculados à demanda; `historico` (opcional, até 4 turnos) é contexto sanitizado, nunca instrução.")
    @ApiResponse(responseCode = "200", description = "Resposta com citações")
    @ApiResponse(responseCode = "403", description = "`demandaId` de demanda fora do escopo do usuário (FISCAL)")
    @ApiResponse(responseCode = "404", description = "`demandaId` inexistente")
    @PostMapping("/perguntas")
    public RespostaAssistente perguntar(@AuthenticationPrincipal UsuarioAutenticado ator,
                                        @Valid @RequestBody PerguntaRequest pedido) {
        return service.perguntar(ator, pedido);
    }
}
