package br.com.nexusbuilderlab.semanexus.docia.assistente;

import br.com.nexusbuilderlab.semanexus.agentes.EventoAgente;
import br.com.nexusbuilderlab.semanexus.agentes.TransmissaoAgente;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.PerguntaRequest;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.RespostaAssistente;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
    private final TransmissaoAgente transmissao;

    public AssistenteController(AssistenteService service, TransmissaoAgente transmissao) {
        this.service = service;
        this.transmissao = transmissao;
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

    @Operation(summary = "Pergunta sobre os documentos por streaming SSE",
            description = "Mesmo corpo, perfis, escopo (`demandaId`: 404/403 como `problem+json`, antes do stream), "
                    + "limite de requisições (categoria IA) e auditoria de `POST /api/docia/perguntas`. Resposta "
                    + "`text/event-stream` com eventos nomeados e `data` em JSON: `inicio` {agente}, "
                    + "`ferramenta_iniciada` {ferramenta, ordem}, `ferramenta_concluida` {ferramenta, ordem, resumo, erro}, "
                    + "`fallback_demonstrativo` {motivo} (segue no modo extrativo), `delta_texto` {texto} (a resposta, num "
                    + "único evento — os provedores ainda não transmitem tokens), `concluido` (o mesmo JSON "
                    + "`RespostaAssistente` do endpoint síncrono) ou `erro` {mensagem, referencia}. Comentários "
                    + "`:keepalive` periódicos. JWT só no cabeçalho `Authorization`. Issue #135.")
    @ApiResponse(responseCode = "200", description = "Stream SSE dos passos do agente",
            content = @Content(mediaType = "text/event-stream", schema = @Schema(implementation = EventoAgente.class)))
    @ApiResponse(responseCode = "403", description = "`demandaId` de demanda fora do escopo do usuário (FISCAL)")
    @ApiResponse(responseCode = "404", description = "`demandaId` inexistente")
    @PostMapping("/perguntas/stream")
    public ResponseEntity<SseEmitter> perguntarStream(@AuthenticationPrincipal UsuarioAutenticado ator,
                                                      @Valid @RequestBody PerguntaRequest pedido) {
        Set<Long> permitidos = service.documentosPermitidos(ator, pedido);
        return transmissao.transmitir("docia", obs -> service.responder(pedido, permitidos, obs),
                RespostaAssistente::resposta);
    }
}
