package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.PrazoDtos.EstadoPrazo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.PrazoDtos.PrazoItem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.PrazoDtos.PrazoResumo;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controle de prazos do Flux (Issue #84). Escopo por perfil igual ao da listagem de demandas. */
@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/flux/prazos")
@Tag(name = "Flux — Prazos", description = "Controle de prazos das demandas (dados fictícios: demonstrativo=true)")
public class PrazoController {

    private final PrazoService service;

    public PrazoController(PrazoService service) {
        this.service = service;
    }

    @Operation(summary = "Resumo de prazos: vencidas, próximas, no prazo e concluídas (no prazo/com atraso)",
            description = "Considera só demandas com prazo definido. FISCAL enxerga apenas as suas ou da sua equipe.")
    @GetMapping("/resumo")
    public PrazoResumo resumo(@AuthenticationPrincipal UsuarioAutenticado ator,
                              @Parameter(description = "Janela de alerta em dias (0-365; padrão 5)")
                              @RequestParam(required = false) Integer diasAlerta) {
        return service.resumo(ator, diasAlerta);
    }

    @Operation(summary = "Lista demandas com prazo por estado (VENCIDA, PROXIMA, NO_PRAZO, CONCLUIDA)",
            description = "Ordenadas por prazo. Sem `estado`, todas com prazo. Paginação `page`/`size`.")
    @GetMapping
    public PageResponse<PrazoItem> listar(@AuthenticationPrincipal UsuarioAutenticado ator,
                                          @RequestParam(required = false) EstadoPrazo estado,
                                          @RequestParam(required = false) Integer diasAlerta,
                                          @ParameterObject Pageable paginacao) {
        return service.listar(ator, estado, diasAlerta, paginacao);
    }
}
