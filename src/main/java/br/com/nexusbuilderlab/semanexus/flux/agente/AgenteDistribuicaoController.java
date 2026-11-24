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
public class AgenteDistribuicaoController {

    private final AgenteDistribuicaoService service;

    public AgenteDistribuicaoController(AgenteDistribuicaoService service) {
        this.service = service;
    }

    @Operation(summary = "Sugestão do Agente de Distribuição (não grava nada)",
            description = "Perfis: ADMIN, GESTOR (os mesmos da distribuição). Demanda em AGUARDANDO_DISTRIBUICAO "
                    + "(senão 409; o fluxo não tem redistribuição). O agente consulta a carga de trabalho das equipes "
                    + "do núcleo regional (`carga_trabalho_fiscais`), as vistorias em campo no município/região "
                    + "(`demandas_ativas_na_regiao`) e o tempo médio histórico da categoria (`tempo_medio_categoria`) "
                    + "e sugere equipe ativa, fiscal membro dela e prazo (>= hoje). O servidor valida a sugestão da IA; "
                    + "se inválida ou sem IA, usa as regras locais (`modo` DEMONSTRATIVO). `modo`: LOCAL, CLAUDE ou "
                    + "DEMONSTRATIVO. Para aplicar, o usuário usa `POST /api/demandas/{id}/distribuicao`. Issue #130.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR)
    @PostMapping("/{id}/distribuicao/sugestao")
    public SugestaoDistribuicao sugerir(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id) {
        return service.sugerir(ator, id);
    }
}
