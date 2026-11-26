package br.com.nexusbuilderlab.semanexus.core.sistema;

import br.com.nexusbuilderlab.semanexus.security.Autorizacao;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sistema")
@Tag(name = "Painel do sistema", description = "Saúde ao vivo dos componentes e operação dos agentes (Issue #114)")
public class PainelSistemaController {

    private final PainelSistemaService service;

    public PainelSistemaController(PainelSistemaService service) {
        this.service = service;
    }

    @Operation(summary = "Painel do sistema (somente ADMIN)",
            description = "Verificações ao vivo (API, banco Oracle, IA dos agentes, dados públicos do INPE, "
                    + "armazenamento) com estado OK/ATENCAO/INDISPONIVEL, latência e detalhes, as métricas dos "
                    + "agentes desde que a API subiu e `governanca` (Issue #131: concordância Humano × IA por agente/"
                    + "categoria e feedback do DocIA nos últimos 30 dias). Falha de um componente não derruba o painel. Nunca expõe "
                    + "chaves ou senhas.")
    @PreAuthorize(Autorizacao.SOMENTE_ADMIN)
    @GetMapping("/painel")
    public PainelSistema painel() {
        return service.montar();
    }
}
