package br.com.nexusbuilderlab.semanexus.core.notificacao;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.core.notificacao.dto.ContagemNaoLidas;
import br.com.nexusbuilderlab.semanexus.core.notificacao.dto.NotificacaoDto;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.JwtAuthFilter;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notificacoes")
@Tag(name = "Core — Notificações", description = "Notificações do usuário logado, com marcação de leitura")
public class NotificacaoController {

    private final NotificacaoService service;
    private final CanalNotificacoes canal;

    public NotificacaoController(NotificacaoService service, CanalNotificacoes canal) {
        this.service = service;
        this.canal = canal;
    }

    @Operation(summary = "Stream SSE das notificações do usuário logado, em tempo real",
            description = "Qualquer perfil autenticado; cada usuário recebe apenas as próprias notificações. "
                    + "Autenticação **somente** pelo cabeçalho `Authorization: Bearer` (o token nunca vai na query "
                    + "string; o cliente usa `fetch` com leitura do corpo em stream). Eventos: `conectado` "
                    + "(`{\"naoLidas\": n}`, ao abrir) e `notificacao` (mesmo JSON de `GET /api/notificacoes`, "
                    + "enviado só depois do commit); comentário `:keepalive` a cada "
                    + "`NOTIFICACOES_SSE_BATIMENTO_SEGUNDOS` (padrão 15). O servidor encerra o stream após "
                    + "`NOTIFICACOES_SSE_DURACAO_MINUTOS` (padrão 15), na expiração do token ou no logout: o "
                    + "cliente reconecta (e cai no polling se não conseguir). Até `NOTIFICACOES_SSE_MAX_POR_USUARIO` "
                    + "(3) streams por usuário — o mais antigo é fechado; acima de `NOTIFICACOES_SSE_MAX_SIMULTANEOS` "
                    + "(200) no total, 503 problem+json.")
    @ApiResponse(responseCode = "200", description = "Stream text/event-stream",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE))
    @ApiResponse(responseCode = "401", description = "Sem token, token inválido, expirado ou revogado")
    @ApiResponse(responseCode = "503", description = "Conexões de notificação demais abertas")
    @PreAuthorize(Autorizacao.AUTENTICADO)
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal UsuarioAutenticado ator,
                                             HttpServletRequest request) {
        long naoLidas = service.contarNaoLidas(ator).naoLidas();
        if (request.getAttribute(JwtAuthFilter.ATRIBUTO_TOKEN) instanceof JwtService.TokenValido token) {
            return canal.abrir(ator.id(), token.jti(), token.expiraEm(), naoLidas);
        }
        return canal.abrir(ator.id(), null, null, naoLidas);
    }

    @Operation(summary = "Lista as notificações do usuário logado, mais recentes primeiro",
            description = "Qualquer perfil autenticado; cada usuário vê apenas as próprias. "
                    + "Filtro opcional `lida=true|false`.")
    @PreAuthorize(Autorizacao.AUTENTICADO)
    @GetMapping
    public PageResponse<NotificacaoDto> listar(@AuthenticationPrincipal UsuarioAutenticado ator,
                                               @Parameter(description = "Filtra por lida / não lida")
                                               @RequestParam(required = false) Boolean lida,
                                               @ParameterObject Pageable paginacao) {
        return service.listar(ator, lida, paginacao);
    }

    @Operation(summary = "Contador de notificações não lidas do usuário logado")
    @PreAuthorize(Autorizacao.AUTENTICADO)
    @GetMapping("/nao-lidas/contagem")
    public ContagemNaoLidas contarNaoLidas(@AuthenticationPrincipal UsuarioAutenticado ator) {
        return service.contarNaoLidas(ator);
    }

    @Operation(summary = "Marca a notificação como lida",
            description = "404 se não existir ou pertencer a outro usuário. Idempotente.")
    @PreAuthorize(Autorizacao.AUTENTICADO)
    @PatchMapping("/{id}/lida")
    public NotificacaoDto marcarLida(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id) {
        return service.marcarLida(ator, id);
    }
}
