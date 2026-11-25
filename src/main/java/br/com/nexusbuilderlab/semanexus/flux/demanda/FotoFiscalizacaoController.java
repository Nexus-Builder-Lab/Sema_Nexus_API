package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ChecklistModelo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FotoFiscalizacao;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Checklists por categoria e fotos de evidência com EXIF da fiscalização (Issue #145). Simulação demonstrativa:
 * não gera auto de infração nem substitui sistemas oficiais.
 */
@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/demandas")
@Tag(name = "Flux — Fiscalização: checklist e fotos",
        description = "Modelos de checklist por categoria e fotos de evidência (JPEG/PNG/WebP) com leitura de EXIF")
public class FotoFiscalizacaoController {

    private final FotoFiscalizacaoService service;

    public FotoFiscalizacaoController(FotoFiscalizacaoService service) {
        this.service = service;
    }

    @Operation(summary = "Modelos de checklist da fiscalização por categoria",
            description = "Qualquer perfil autenticado. Modelos versionados no código; o checklist enviado em "
                    + "`POST /api/demandas/{id}/fiscalizacao` é validado contra o modelo da categoria da demanda.")
    @ApiResponse(responseCode = "200", description = "Um modelo por categoria",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChecklistModelo.class))))
    @GetMapping("/checklists")
    public List<ChecklistModelo> checklists() {
        return ChecklistFiscalizacao.modelos();
    }

    @Operation(summary = "Envia uma foto de evidência (multipart, parte `arquivo`)",
            description = "Perfis: FISCAL (só o fiscal designado), GESTOR, ADMIN. Demanda em EM_ANDAMENTO (409 caso "
                    + "contrário ou com 20 fotos). JPEG, PNG ou WebP reconhecidos pelos magic bytes (outro conteúdo → 415), "
                    + "até 10 MB (413). Arquivo gravado intacto no volume, com SHA-256; EXIF (data/hora, GPS) lido só "
                    + "como informação — nunca usado em autorização — e GPS fora de MT é sinalizado. Distância em km até "
                    + "a coordenada da demanda ou até a borda da área. Registra histórico e auditoria.")
    @ApiResponse(responseCode = "201", description = "Foto registrada",
            content = @Content(schema = @Schema(implementation = FotoFiscalizacao.class)))
    @ApiResponse(responseCode = "403", description = "Fiscal que não é o designado para a demanda")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR_FISCAL)
    @PostMapping(value = "/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FotoFiscalizacao> enviar(@AuthenticationPrincipal UsuarioAutenticado ator,
                                                   @PathVariable Long id,
                                                   @RequestPart("arquivo") MultipartFile arquivo) {
        FotoFiscalizacao foto = service.enviar(ator, id, arquivo);
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{fotoId}").buildAndExpand(foto.id()).toUri();
        return ResponseEntity.created(local).body(foto);
    }

    @Operation(summary = "Lista as fotos de evidência da demanda (metadados e selo de conferência do EXIF)",
            description = "Qualquer perfil autenticado; FISCAL só nas demandas da sua equipe ou atribuídas a ele.")
    @ApiResponse(responseCode = "200", description = "Fotos da demanda, das mais antigas às mais recentes",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FotoFiscalizacao.class))))
    @ApiResponse(responseCode = "403", description = "Fiscal fora do escopo (demanda de outra equipe)")
    @GetMapping("/{id}/fotos")
    public List<FotoFiscalizacao> listar(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id) {
        return service.listar(ator, id);
    }

    @Operation(summary = "Baixa o arquivo da foto",
            description = "Qualquer perfil autenticado com acesso à demanda (FISCAL só no seu escopo). "
                    + "`Content-Disposition: attachment` com nome saneado e `X-Content-Type-Options: nosniff`.",
            responses = @ApiResponse(responseCode = "200", description = "Conteúdo da imagem",
                    content = {
                        @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
                        @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")),
                        @Content(mediaType = "image/webp", schema = @Schema(type = "string", format = "binary"))}))
    @GetMapping(value = "/{id}/fotos/{fotoId}/arquivo")
    public ResponseEntity<byte[]> baixar(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id,
                                         @PathVariable Long fotoId) {
        FotoFiscalizacaoService.FotoBaixada foto = service.baixar(ator, id, fotoId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(foto.nome(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(foto.mime()))
                .contentLength(foto.conteudo().length)
                .body(foto.conteudo());
    }

    @Operation(summary = "Exclui uma foto de evidência",
            description = "Perfis: FISCAL, GESTOR, ADMIN — e somente quem enviou a foto (403 caso contrário), antes da "
                    + "conclusão da demanda (409 se FINALIZADA). Registra histórico e auditoria.")
    @ApiResponse(responseCode = "204", description = "Foto excluída")
    @ApiResponse(responseCode = "403", description = "Usuário não é quem enviou a foto ou está fora do escopo")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR_FISCAL)
    @DeleteMapping("/{id}/fotos/{fotoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id,
                        @PathVariable Long fotoId) {
        service.excluir(ator, id, fotoId);
    }
}
