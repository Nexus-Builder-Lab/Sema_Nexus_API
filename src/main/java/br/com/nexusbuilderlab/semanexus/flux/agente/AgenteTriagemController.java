package br.com.nexusbuilderlab.semanexus.flux.agente;

import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demandas")
@Tag(name = "Agentes de IA", description = "Agentes que rodam dentro do sistema e sugerem ações (Issue #106)")
public class AgenteTriagemController {

    private final AgenteTriagemService service;

    public AgenteTriagemController(AgenteTriagemService service) {
        this.service = service;
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
}
