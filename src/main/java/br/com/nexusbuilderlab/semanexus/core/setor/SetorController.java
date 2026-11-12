package br.com.nexusbuilderlab.semanexus.core.setor;

import br.com.nexusbuilderlab.semanexus.core.setor.dto.SetorDto;
import br.com.nexusbuilderlab.semanexus.core.setor.dto.SetorRequest;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setores")
@Tag(name = "Estrutura", description = "Setores, equipes e membros (consulta)")
public class SetorController {

    private final SetorService service;

    public SetorController(SetorService service) {
        this.service = service;
    }

    @Operation(summary = "Lista os setores",
            description = "Perfis: ADMIN e GESTOR. Ordenado por sigla; sem paginação (lista curta).")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR)
    @GetMapping
    public List<SetorDto> listar(
            @Parameter(description = "Filtra por situação; sem o parâmetro traz ativos e inativos")
            @RequestParam(required = false) Boolean ativo) {
        return service.listar(ativo);
    }

    @Operation(summary = "Cadastra um setor",
            description = "Somente ADMIN. Sigla duplicada responde 409 (sigla gravada em maiúsculas).")
    @ApiResponse(responseCode = "201", description = "Setor criado; `Location` aponta para `/api/setores/{id}`")
    @PreAuthorize(Autorizacao.SOMENTE_ADMIN)
    @PostMapping
    public ResponseEntity<SetorDto> criar(@Valid @RequestBody SetorRequest pedido) {
        SetorDto criado = service.criar(pedido);
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    @Operation(summary = "Edita um setor", description = "Somente ADMIN. 404 se não existir; sigla duplicada 409.")
    @PreAuthorize(Autorizacao.SOMENTE_ADMIN)
    @PutMapping("/{id}")
    public SetorDto atualizar(@PathVariable Long id, @Valid @RequestBody SetorRequest pedido) {
        return service.atualizar(id, pedido);
    }
}
