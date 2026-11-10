package br.com.nexusbuilderlab.semanexus.core.equipe;

import br.com.nexusbuilderlab.semanexus.core.equipe.dto.EquipeDto;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.MembroDto;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/equipes")
@Tag(name = "Estrutura", description = "Setores, equipes e membros (consulta)")
public class EquipeController {

    private final EquipeService service;

    public EquipeController(EquipeService service) {
        this.service = service;
    }

    @Operation(summary = "Lista as equipes",
            description = "Perfis: ADMIN e GESTOR. Ordenado por nome; sem paginação (lista curta).")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR)
    @GetMapping
    public List<EquipeDto> listar(
            @Parameter(description = "Filtra pelas equipes de um setor") @RequestParam(required = false) Long setorId,
            @Parameter(description = "Filtra por situação; sem o parâmetro traz ativas e inativas")
            @RequestParam(required = false) Boolean ativo) {
        return service.listar(setorId, ativo);
    }

    @Operation(summary = "Lista os membros (fiscais e demais usuários) de uma equipe",
            description = "Perfis: ADMIN e GESTOR. 404 se a equipe não existir.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR)
    @GetMapping("/{id}/membros")
    public List<MembroDto> membros(@PathVariable Long id) {
        return service.membros(id);
    }
}
