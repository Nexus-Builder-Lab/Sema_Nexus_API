package br.com.nexusbuilderlab.semanexus.docia.documento;

import br.com.nexusbuilderlab.semanexus.common.exception.ContratoPendenteException;
import br.com.nexusbuilderlab.semanexus.common.openapi.ContratoPendente;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoResumo;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.VinculoDocumentoRequest;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Associação documento ↔ demanda (contrato; implementação na Issue #16). */
@RestController
@RequestMapping("/api/demandas/{id}/documentos")
@Tag(name = "DocIA — Documentos")
public class DemandaDocumentoController {

    @Operation(summary = "Documentos vinculados à demanda",
            description = "Qualquer perfil autenticado; FISCAL só nas demandas da sua equipe ou atribuídas a ele.")
    @ApiResponse(responseCode = "403", description = "Fiscal fora do escopo (demanda de outra equipe)")
    @GetMapping
    @ContratoPendente(issue = 16)
    public List<DocumentoResumo> listar(@PathVariable Long id) {
        throw new ContratoPendenteException(16);
    }

    @Operation(summary = "Vincula um documento existente à demanda",
            description = "Perfis: ADMIN, GESTOR, FISCAL, ANALISTA. Vínculo duplicado responde 409.")
    @ApiResponse(responseCode = "201", description = "Vínculo criado")
    @PreAuthorize(Autorizacao.ESCRITA_DOCUMENTO)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ContratoPendente(issue = 16)
    public DocumentoResumo vincular(@PathVariable Long id, @Valid @RequestBody VinculoDocumentoRequest pedido) {
        throw new ContratoPendenteException(16);
    }

    @Operation(summary = "Remove o vínculo entre o documento e a demanda (o documento permanece na biblioteca)",
            description = "Perfis: ADMIN, GESTOR, FISCAL, ANALISTA.")
    @PreAuthorize(Autorizacao.ESCRITA_DOCUMENTO)
    @DeleteMapping("/{documentoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ContratoPendente(issue = 16)
    public void desvincular(@PathVariable Long id, @PathVariable Long documentoId) {
        throw new ContratoPendenteException(16);
    }
}
