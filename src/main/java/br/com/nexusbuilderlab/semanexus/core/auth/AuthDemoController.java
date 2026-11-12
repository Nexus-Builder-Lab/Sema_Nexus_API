package br.com.nexusbuilderlab.semanexus.core.auth;

import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login demonstrativo por perfil. Existe somente no profile {@code demo}; nos demais a rota responde 404. */
@RestController
@Profile("demo")
@RequestMapping("/api/auth/demo")
@Tag(name = "Autenticação", description = "Login JWT, logout, usuário corrente e configuração pública de login")
public class AuthDemoController {

    private final AuthService service;

    public AuthDemoController(AuthService service) {
        this.service = service;
    }

    @Operation(summary = "Login demonstrativo por perfil (somente profile demo)",
            description = "Devolve o mesmo LoginResponse do login para o primeiro usuário ativo e demonstrativo do perfil. "
                    + "Perfis: ADMIN, GESTOR, FISCAL, ANALISTA, CONSULTA. AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.")
    @ApiResponse(responseCode = "200", description = "Token JWT, validade em segundos e dados do usuário",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(responseCode = "400", description = "Perfil inválido.")
    @ApiResponse(responseCode = "404", description = "Nenhum usuário demonstrativo ativo para o perfil.")
    @SecurityRequirements
    @PostMapping("/{perfil}")
    public LoginResponse entrar(@PathVariable String perfil) {
        return service.loginDemo(perfil);
    }
}
