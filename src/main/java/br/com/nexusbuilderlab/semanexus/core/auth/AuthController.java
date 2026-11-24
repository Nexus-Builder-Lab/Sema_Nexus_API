package br.com.nexusbuilderlab.semanexus.core.auth;

import br.com.nexusbuilderlab.semanexus.core.auth.dto.AuthConfigResponse;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginRequest;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginResponse;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.UsuarioResumo;
import br.com.nexusbuilderlab.semanexus.security.JwtAuthFilter;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Login JWT, logout, usuário corrente e configuração pública de login")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @Operation(summary = "Autentica com e-mail e senha e devolve o token JWT",
            description = "401 para credenciais inválidas (mensagem genérica); 403 para usuário inativo; 429 "
                    + "(problem+json com Retry-After) acima de LIMITE_LOGIN_POR_JANELA tentativas por IP na janela.")
    @ApiResponse(responseCode = "200", description = "Token JWT, validade em segundos e dados do usuário",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos (mensagem genérica).")
    @ApiResponse(responseCode = "403", description = "Usuário inativo.")
    @SecurityRequirements
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest pedido) {
        return service.login(pedido);
    }

    @Operation(summary = "Encerra a sessão: revoga o token na hora e registra o logout na auditoria",
            description = "O jti do token vai para a lista de revogados (Oracle); qualquer requisição seguinte com o "
                    + "mesmo token recebe 401, inclusive um novo logout. O cliente também descarta o token.")
    @ApiResponse(responseCode = "204", description = "Token revogado")
    @ApiResponse(responseCode = "401", description = "Sem token, token inválido, expirado ou já revogado.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest request) {
        Object atributo = request.getAttribute(JwtAuthFilter.ATRIBUTO_TOKEN);
        if (atributo instanceof JwtService.TokenValido token) {
            service.logout(usuario.id(), token.jti(), token.expiraEm());
        } else {
            service.logout(usuario.id(), null, null);
        }
    }

    @Operation(summary = "Dados do usuário autenticado")
    @GetMapping("/me")
    public UsuarioResumo me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return service.me(usuario.id());
    }

    @Operation(summary = "Configuração pública da tela de login",
            description = "demo=true quando o profile demo está ativo (aviso AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS).")
    @SecurityRequirements
    @GetMapping("/config")
    public AuthConfigResponse configuracao() {
        return service.configuracao();
    }
}
