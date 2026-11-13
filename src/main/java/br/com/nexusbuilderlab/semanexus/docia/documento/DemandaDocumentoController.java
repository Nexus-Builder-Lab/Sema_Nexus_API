package br.com.nexusbuilderlab.semanexus.docia.documento;

import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoResumo;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.VinculoDocumentoRequest;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Associação documento ↔ demanda (Issue #16): vínculo registrado no histórico da demanda e na auditoria. */
@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/demandas/{id}/documentos")
@Tag(name = "DocIA — Documentos")
public class DemandaDocumentoController {

    private final DocumentoService service;

    public DemandaDocumentoController(DocumentoService service) {
        this.service = service;
    }

    @Operation(summary = "Documentos vinculados à demanda",
            description = "Qualquer perfil autenticado; FISCAL só nas demandas da sua equipe ou atribuídas a ele.")
    @ApiResponse(responseCode = "200", description = "Documentos vinculados à demanda",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DocumentoResumo.class))))
    @ApiResponse(responseCode = "403", description = "Fiscal fora do escopo (demanda de outra equipe)")
    @GetMapping
    public List<DocumentoResumo> listar(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id) {
        return service.listarDaDemanda(ator, id);
    }

    @Operation(summary = "Vincula um documento existente à demanda",
            description = "Perfis: ADMIN, GESTOR, FISCAL, ANALISTA. Vínculo duplicado responde 409.")
    @ApiResponse(responseCode = "409", description = "Documento já vinculado à demanda")
    @ApiResponse(responseCode = "201", description = "Vínculo criado")
    @PreAuthorize(Autorizacao.ESCRITA_DOCUMENTO)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoResumo vincular(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id,
                                   @Valid @RequestBody VinculoDocumentoRequest pedido) {
        return service.vincular(ator, id, pedido.documentoId());
    }

    @Operation(summary = "Remove o vínculo entre o documento e a demanda (o documento permanece na biblioteca)",
            description = "Perfis: ADMIN, GESTOR, FISCAL, ANALISTA.")
    @PreAuthorize(Autorizacao.ESCRITA_DOCUMENTO)
    @DeleteMapping("/{documentoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desvincular(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id,
                            @PathVariable Long documentoId) {
        service.desvincular(ator, id, documentoId);
    }
}
