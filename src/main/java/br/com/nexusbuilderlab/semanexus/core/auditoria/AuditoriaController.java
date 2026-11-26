package br.com.nexusbuilderlab.semanexus.core.auditoria;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.AuditoriaDto;
import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.AuditoriaFiltro;
import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.IntegridadeAuditoria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    private final IntegridadeAuditoriaService integridade;

    public AuditoriaController(AuditService service, IntegridadeAuditoriaService integridade) {
        this.service = service;
        this.integridade = integridade;
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

    @Operation(summary = "Confere a integridade da trilha de auditoria encadeada (SHA-256)",
            description = "Perfil: ADMIN (demais recebem 403). Recalcula o hash de cada registro selado, em ordem de "
                    + "`seqCadeia`, e confere o encadeamento (`hashAnterior`) e o fim da cadeia. `integra=false` traz "
                    + "a primeira falha (`HASH_DIVERGENTE`, `ENCADEAMENTO_QUEBRADO`, `SEQUENCIA_AUSENTE` ou "
                    + "`FIM_DIVERGENTE`). Registros anteriores à cadeia aparecem em `registrosLegados` (sem hash, "
                    + "integridade não garantida); os recém-gravados ainda não selados, em `registrosPendentes`. "
                    + "Somente leitura.")
    @ApiResponse(responseCode = "200", description = "Resultado da conferência",
            content = @Content(schema = @Schema(implementation = IntegridadeAuditoria.class)))
    @ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/integridade")
    public IntegridadeAuditoria integridade() {
        return integridade.verificar();
    }
}
