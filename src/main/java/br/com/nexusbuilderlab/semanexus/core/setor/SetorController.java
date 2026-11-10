package br.com.nexusbuilderlab.semanexus.core.setor;

import br.com.nexusbuilderlab.semanexus.core.setor.dto.SetorDto;
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
}
