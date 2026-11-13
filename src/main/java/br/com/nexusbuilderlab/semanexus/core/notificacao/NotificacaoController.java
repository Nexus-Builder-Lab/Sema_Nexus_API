package br.com.nexusbuilderlab.semanexus.core.notificacao;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.core.notificacao.dto.ContagemNaoLidas;
import br.com.nexusbuilderlab.semanexus.core.notificacao.dto.NotificacaoDto;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notificacoes")
@Tag(name = "Core — Notificações", description = "Notificações do usuário logado, com marcação de leitura")
public class NotificacaoController {

    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) {
        this.service = service;
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
