package br.com.nexusbuilderlab.semanexus.core.equipe;

import br.com.nexusbuilderlab.semanexus.core.equipe.dto.EquipeDto;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.EquipeRequest;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.MembroDto;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.MembrosRequest;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
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

    @Operation(summary = "Cadastra uma equipe",
            description = "Somente ADMIN. O responsável (usuário ativo, opcional) passa a ser membro. "
                    + "Nome repetido no setor responde 409.")
    @ApiResponse(responseCode = "201", description = "Equipe criada; `Location` aponta para `/api/equipes/{id}`")
    @PreAuthorize(Autorizacao.SOMENTE_ADMIN)
    @PostMapping
    public ResponseEntity<EquipeDto> criar(@Valid @RequestBody EquipeRequest pedido) {
        EquipeDto criada = service.criar(pedido);
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(local).body(criada);
    }

    @Operation(summary = "Edita uma equipe (nome, setor, situação e responsável)",
            description = "Somente ADMIN. 404 se não existir; nome repetido no setor 409.")
    @PreAuthorize(Autorizacao.SOMENTE_ADMIN)
    @PutMapping("/{id}")
    public EquipeDto atualizar(@PathVariable Long id, @Valid @RequestBody EquipeRequest pedido) {
        return service.atualizar(id, pedido);
    }

    @Operation(summary = "Define os membros (e o responsável) da equipe",
            description = "Somente ADMIN. Substitui a lista de membros; só usuários ativos (senão 400).")
    @PreAuthorize(Autorizacao.SOMENTE_ADMIN)
    @PutMapping("/{id}/membros")
    public EquipeDto definirMembros(@PathVariable Long id, @Valid @RequestBody MembrosRequest pedido) {
        return service.definirMembros(id, pedido);
    }
}
