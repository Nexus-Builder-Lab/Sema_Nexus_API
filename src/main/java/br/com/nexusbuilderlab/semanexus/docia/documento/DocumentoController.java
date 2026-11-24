package br.com.nexusbuilderlab.semanexus.docia.documento;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoDetalhe;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoFiltro;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoResumo;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoUploadRequest;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.PreAnaliseDocumento;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * DocIA (documentos): upload e download (#15); biblioteca, filtros e detalhes (#16).
 * O arquivo fica no volume ({@code STORAGE_DIR}); o Oracle guarda só metadados e texto extraído.
 */
@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/documentos")
@Tag(name = "DocIA — Documentos", description = "Upload, biblioteca documental, detalhes, texto extraído e download")
public class DocumentoController {

    private final DocumentoService service;

    public DocumentoController(DocumentoService service) {
        this.service = service;
    }

    @Operation(summary = "Envia um documento (multipart: metadados + parte `arquivo`)",
            description = "Perfis: ADMIN, GESTOR, FISCAL, ANALISTA (CONSULTA não envia). Extensões pdf, txt, md, csv, docx; "
                    + "máximo 10 MB; o tipo é verificado pelos bytes iniciais. Extração de texto: PDF (tabelas e OCR opcional), DOCX (com tabelas), TXT, MD e CSV.")
    @ApiResponse(responseCode = "201", description = "Documento criado; `Location` aponta para `/api/documentos/{id}`")
    @ApiResponse(responseCode = "413", description = "Arquivo acima de 10 MB")
    @ApiResponse(responseCode = "415", description = "Extensão não permitida ou conteúdo incompatível com a extensão")
    @PreAuthorize(Autorizacao.ESCRITA_DOCUMENTO)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentoDetalhe> enviar(@AuthenticationPrincipal UsuarioAutenticado ator,
                                                   @Valid @ModelAttribute DocumentoUploadRequest pedido) {
        DocumentoDetalhe criado = service.enviar(ator, pedido);
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    @Operation(summary = "Pré-análise: metadados sugeridos para um arquivo, sem salvar",
            description = "Perfis: ADMIN, GESTOR, FISCAL, ANALISTA (mesmos do upload; CONSULTA não). Recebe a parte `arquivo` e devolve "
                    + "título, município, data, palavras-chave e vínculos DEM-AAAA/NNNN sugeridos (regras determinísticas) e avisos "
                    + "(ex.: OCR indisponível). Nada é gravado; o usuário confirma e edita.")
    @ApiResponse(responseCode = "200", description = "Metadados sugeridos (nada é gravado)")
    @ApiResponse(responseCode = "413", description = "Arquivo acima de 10 MB")
    @ApiResponse(responseCode = "415", description = "Extensão não permitida ou conteúdo incompatível com a extensão")
    @PreAuthorize(Autorizacao.ESCRITA_DOCUMENTO)
    @PostMapping(value = "/pre-analise", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PreAnaliseDocumento preAnalisar(@RequestPart("arquivo") MultipartFile arquivo) {
        return service.preAnalisar(arquivo);
    }

    @Operation(summary = "Biblioteca documental: pesquisa textual, filtros combináveis, paginação e ordenação",
            description = "Qualquer perfil autenticado. Ordenação: `sort=campo,asc|desc` "
                    + "(campos: titulo, dataDocumento, categoria, criadoEm). A pesquisa é textual, não semântica.")
    @GetMapping
    public PageResponse<DocumentoResumo> listar(@ParameterObject DocumentoFiltro filtro, @ParameterObject Pageable paginacao) {
        return service.listar(filtro, paginacao);
    }

    @Operation(summary = "Detalhes do documento, com texto extraído e demandas vinculadas",
            description = "Qualquer perfil autenticado.")
    @GetMapping("/{id}")
    public DocumentoDetalhe detalhar(@PathVariable Long id) {
        return service.detalhar(id);
    }

    @Operation(summary = "Baixa o arquivo original",
            description = "Qualquer perfil autenticado. `Content-Disposition: attachment` com nome de arquivo seguro.",
            responses = @ApiResponse(responseCode = "200", description = "Conteúdo do arquivo",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary"))))
    @GetMapping(value = "/{id}/arquivo", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> baixar(@PathVariable Long id) {
        DocumentoService.ArquivoBaixado arquivo = service.baixar(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo.nome(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(arquivo.conteudo().length)
                .body(arquivo.conteudo());
    }
}
