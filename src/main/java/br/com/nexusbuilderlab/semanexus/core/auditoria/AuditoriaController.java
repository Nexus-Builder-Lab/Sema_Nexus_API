package br.com.nexusbuilderlab.semanexus.core.auditoria;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.AuditoriaDto;
import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.AuditoriaFiltro;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria")
@Tag(name = "Core — Auditoria", description = "Consulta do histórico de operações (somente ADMIN)")
public class AuditoriaController {

    private final AuditService service;

    public AuditoriaController(AuditService service) {
        this.service = service;
    }

    @Operation(summary = "Consulta paginada da auditoria, mais recentes primeiro",
            description = "Perfil: ADMIN (demais recebem 403). Filtros combináveis: `de`/`ate` (AAAA-MM-DD, inclusivos), "
                    + "`usuarioId`, `acao` e `entidade` (igualdade, sem diferenciar maiúsculas).")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PageResponse<AuditoriaDto> listar(@ParameterObject AuditoriaFiltro filtro,
                                             @ParameterObject Pageable paginacao) {
        return service.consultar(filtro, paginacao);
    }
}
