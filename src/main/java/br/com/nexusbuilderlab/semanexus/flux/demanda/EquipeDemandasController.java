package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaResumo;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta das demandas de uma equipe (Issue #12). */
@RestController
@RequestMapping("/api/equipes/{id}/demandas")
@Tag(name = "Flux — Demandas")
public class EquipeDemandasController {

    private final DemandaService service;

    public EquipeDemandasController(DemandaService service) {
        this.service = service;
    }

    @Operation(summary = "Demandas distribuídas a uma equipe, com paginação",
            description = "Perfis: ADMIN, GESTOR. Opcionalmente filtra por status.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR)
    @GetMapping
    public PageResponse<DemandaResumo> listar(
            @AuthenticationPrincipal UsuarioAutenticado ator,
            @PathVariable Long id,
            @Parameter(description = "Filtra por status") @RequestParam(required = false) StatusDemanda status,
            @ParameterObject Pageable paginacao) {
        return service.listarPorEquipe(ator, id, status, paginacao);
    }
}
