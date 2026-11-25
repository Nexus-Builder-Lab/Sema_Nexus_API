package br.com.nexusbuilderlab.semanexus.flux.agente;

import br.com.nexusbuilderlab.semanexus.agentes.EventoAgente;
import br.com.nexusbuilderlab.semanexus.agentes.TransmissaoAgente;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/demandas")
@Tag(name = "Agentes de IA", description = "Agentes que rodam dentro do sistema e sugerem ações (Issue #106)")
public class AgenteTriagemController {

    private final AgenteTriagemService service;
    private final TransmissaoAgente transmissao;

    public AgenteTriagemController(AgenteTriagemService service, TransmissaoAgente transmissao) {
        this.service = service;
        this.transmissao = transmissao;
    }

    @Operation(summary = "Sugestão do Agente de Triagem (não grava nada)",
            description = "Perfis: ADMIN, GESTOR, ANALISTA (os mesmos da triagem). Demanda em PENDENTE ou EM_TRIAGEM "
                    + "(senão 409). O agente consulta a demanda, demandas semelhantes e os dados públicos do INPE no "
                    + "município e sugere decisão, categoria, prioridade, núcleo regional e possíveis duplicatas. "
                    + "`modo`: LOCAL (IA local), CLAUDE ou DEMONSTRATIVO (regras locais). Para aceitar, o usuário usa "
                    + "`POST /api/demandas/{id}/triagem`. Issue #107.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR_ANALISTA)
    @PostMapping("/{id}/triagem/sugestao")
    public SugestaoTriagem sugerir(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id) {
        return service.sugerir(ator, id);
    }

    @Operation(summary = "Sugestão do Agente de Triagem por streaming SSE (não grava nada)",
            description = "Mesmos perfis, escopo, validações (404/403/409 como `problem+json`, antes do stream), limite "
                    + "de requisições (categoria IA) e auditoria de `POST /api/demandas/{id}/triagem/sugestao`. Resposta "
                    + "`text/event-stream` com eventos nomeados (campo `event`) e `data` em JSON: `inicio` {agente}, "
                    + "`ferramenta_iniciada` {ferramenta, ordem}, `ferramenta_concluida` {ferramenta, ordem, resumo, erro}, "
                    + "`fallback_demonstrativo` {motivo} (IA indisponível ou sem resposta a tempo; segue pelas regras "
                    + "locais), `delta_texto` {texto} (a justificativa, num único evento — os provedores ainda não "
                    + "transmitem tokens), `concluido` (o mesmo JSON `SugestaoTriagem` do endpoint síncrono) ou `erro` "
                    + "{mensagem, referencia} (sem detalhe interno). Comentários `:keepalive` periódicos. JWT só no "
                    + "cabeçalho `Authorization` (nunca na URL). Issue #135.")
    @ApiResponse(responseCode = "200", description = "Stream SSE dos passos do agente",
            content = @Content(mediaType = "text/event-stream", schema = @Schema(implementation = EventoAgente.class)))
    @ApiResponse(responseCode = "409", description = "Demanda fora de PENDENTE/EM_TRIAGEM (antes de abrir o stream)")
    @ApiResponse(responseCode = "503", description = "Capacidade de streams de agentes esgotada (`AGENTES_SSE_MAX_SIMULTANEOS`); tente de novo ou use o endpoint JSON")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR_ANALISTA)
    @PostMapping("/{id}/triagem/sugestao/stream")
    public ResponseEntity<SseEmitter> sugerirStream(@AuthenticationPrincipal UsuarioAutenticado ator,
                                                    @PathVariable Long id) {
        DemandaDetalhe d = service.verificar(ator, id);
        return transmissao.transmitir(AgenteTriagemService.NOME, obs -> service.sugerir(ator, d, obs),
                SugestaoTriagem::justificativa);
    }
}
