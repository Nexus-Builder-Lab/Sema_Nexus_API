package br.com.nexusbuilderlab.semanexus.core.auth;

import br.com.nexusbuilderlab.semanexus.core.auth.dto.AuthConfigResponse;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginRequest;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginResponse;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.UsuarioResumo;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
            description = "401 para credenciais inválidas (mensagem genérica); 403 para usuário inativo.")
    @SecurityRequirements
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest pedido) {
        return service.login(pedido);
    }

    @Operation(summary = "Registra o logout na auditoria (o token é descartado no cliente)")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.logout(usuario.id());
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
