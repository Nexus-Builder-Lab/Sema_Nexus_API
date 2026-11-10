package br.com.nexusbuilderlab.semanexus.docia.documento;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.common.exception.ContratoPendenteException;
import br.com.nexusbuilderlab.semanexus.common.openapi.ContratoPendente;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoDetalhe;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoFiltro;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoResumo;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoUploadRequest;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato do DocIA (documentos). Os endpoints respondem {@code 501} até a Issue de cada
 * {@link ContratoPendente}: upload e download (#15); biblioteca, filtros e detalhes (#16).
 * O arquivo fica no volume ({@code STORAGE_DIR}); o Oracle guarda só metadados e texto extraído.
 */
@RestController
@RequestMapping("/api/documentos")
@Tag(name = "DocIA — Documentos", description = "Upload, biblioteca documental, detalhes, texto extraído e download")
public class DocumentoController {

    @Operation(summary = "Envia um documento (multipart: metadados + parte `arquivo`)",
            description = "Perfis: ADMIN, GESTOR, FISCAL, ANALISTA (CONSULTA não envia). Extensões pdf, txt, md, csv, docx; "
                    + "máximo 10 MB; o tipo é verificado pelos bytes iniciais. Extração de texto: PDF, TXT, MD e CSV.")
    @ApiResponse(responseCode = "201", description = "Documento criado; `Location` aponta para `/api/documentos/{id}`")
    @PreAuthorize(Autorizacao.ESCRITA_DOCUMENTO)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @ContratoPendente(issue = 15)
    public DocumentoDetalhe enviar(@Valid @ModelAttribute DocumentoUploadRequest pedido) {
        throw new ContratoPendenteException(15);
    }

    @Operation(summary = "Biblioteca documental: pesquisa textual, filtros combináveis, paginação e ordenação",
            description = "Qualquer perfil autenticado. Ordenação: `sort=campo,asc|desc` "
                    + "(campos: titulo, dataDocumento, categoria, criadoEm). A pesquisa é textual, não semântica.")
    @GetMapping
    @ContratoPendente(issue = 16)
    public PageResponse<DocumentoResumo> listar(@ParameterObject DocumentoFiltro filtro, @ParameterObject Pageable paginacao) {
        throw new ContratoPendenteException(16);
    }

    @Operation(summary = "Detalhes do documento, com texto extraído e demandas vinculadas",
            description = "Qualquer perfil autenticado.")
    @GetMapping("/{id}")
    @ContratoPendente(issue = 16)
    public DocumentoDetalhe detalhar(@PathVariable Long id) {
        throw new ContratoPendenteException(16);
    }

    @Operation(summary = "Baixa o arquivo original",
            description = "Qualquer perfil autenticado. `Content-Disposition: attachment` com nome de arquivo seguro.",
            responses = @ApiResponse(responseCode = "200", description = "Conteúdo do arquivo",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary"))))
    @GetMapping(value = "/{id}/arquivo", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ContratoPendente(issue = 15)
    public ResponseEntity<byte[]> baixar(@PathVariable Long id) {
        throw new ContratoPendenteException(15);
    }
}
