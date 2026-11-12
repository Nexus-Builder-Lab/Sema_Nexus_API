package br.com.nexusbuilderlab.semanexus.core.usuario;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.core.usuario.dto.AtivacaoRequest;
import br.com.nexusbuilderlab.semanexus.core.usuario.dto.UsuarioAtualizacaoRequest;
import br.com.nexusbuilderlab.semanexus.core.usuario.dto.UsuarioCriacaoRequest;
import br.com.nexusbuilderlab.semanexus.core.usuario.dto.UsuarioDto;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuários", description = "Gestão de usuários (cadastro, edição e ativação: somente ADMIN)")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @Operation(summary = "Lista usuários com pesquisa e filtros",
            description = "Perfis: ADMIN e GESTOR. Paginado; ordenado por nome. Nunca expõe senha/hash.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR)
    @GetMapping
    public PageResponse<UsuarioDto> listar(
            @Parameter(description = "Pesquisa por trecho do nome ou e-mail") @RequestParam(required = false) String q,
            @Parameter(description = "Filtra por perfil") @RequestParam(required = false) Perfil perfil,
            @Parameter(description = "Filtra por situação") @RequestParam(required = false) Boolean ativo,
            @ParameterObject Pageable paginacao) {
        return service.listar(q, perfil, ativo, paginacao);
    }

    @Operation(summary = "Detalha um usuário", description = "Perfis: ADMIN e GESTOR. 404 se não existir.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR)
    @GetMapping("/{id}")
    public UsuarioDto buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @Operation(summary = "Cadastra um usuário",
            description = "Somente ADMIN. Senha com 8 a 72 caracteres (senão 400); e-mail duplicado responde 409.")
    @ApiResponse(responseCode = "201", description = "Usuário criado; `Location` aponta para `/api/usuarios/{id}`")
    @PreAuthorize(Autorizacao.SOMENTE_ADMIN)
    @PostMapping
    public ResponseEntity<UsuarioDto> criar(@Valid @RequestBody UsuarioCriacaoRequest pedido) {
        UsuarioDto criado = service.criar(pedido);
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    @Operation(summary = "Edita um usuário",
            description = "Somente ADMIN. `senha` é opcional (redefine; 8 a 72 caracteres). "
                    + "Rebaixar o último ADMIN ativo responde 409.")
    @PreAuthorize(Autorizacao.SOMENTE_ADMIN)
    @PutMapping("/{id}")
    public UsuarioDto atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioAtualizacaoRequest pedido) {
        return service.atualizar(id, pedido);
    }

    @Operation(summary = "Ativa ou desativa um usuário",
            description = "Somente ADMIN. Desativar o último ADMIN ativo responde 409.")
    @PreAuthorize(Autorizacao.SOMENTE_ADMIN)
    @PatchMapping("/{id}/ativacao")
    public UsuarioDto ativacao(@PathVariable Long id, @Valid @RequestBody AtivacaoRequest pedido) {
        return service.alterarAtivacao(id, pedido.ativo());
    }
}
