package br.com.nexusbuilderlab.semanexus.docia.assistente;

import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.PerguntaRequest;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.RespostaAssistente;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import io.swagger.v3.oas.annotations.Operation;
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
                    + "multas ou procedimentos oficiais são recusadas.")
    @PostMapping("/perguntas")
    public RespostaAssistente perguntar(@Valid @RequestBody PerguntaRequest pedido) {
        return service.perguntar(pedido.pergunta());
    }
}
