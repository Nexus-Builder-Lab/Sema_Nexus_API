package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exportação do dossiê completo da demanda em PDF (Issue #146).
 * Respeita o mesmo escopo de consulta da demanda (FISCAL só enxerga demandas da sua equipe ou atribuídas a ele).
 */
@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/demandas")
@Tag(name = "Flux — Demandas")
public class DossieDemandaController {

    private final DossieDemandaService service;

    public DossieDemandaController(DossieDemandaService service) {
        this.service = service;
    }

    @Operation(summary = "Exporta o dossiê completo da demanda em PDF",
            description = "Qualquer perfil autenticado; FISCAL só nas demandas da sua equipe ou atribuídas a ele "
                    + "(403 fora do escopo). Gera PDF demonstrativo com faixa de ambiente fictício, dados cadastrais, "
                    + "croqui vetorial do polígono/coordenada em MT, linha do tempo, fiscalização/checklist/fotos, "
                    + "documentos vinculados com SHA-256 e QR Code com código de verificação de auditoria.")
    @ApiResponse(responseCode = "200", description = "Dossiê da demanda em PDF",
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "403", description = "Fiscal fora do escopo (demanda de outra equipe)")
    @ApiResponse(responseCode = "404", description = "Demanda não encontrada")
    @GetMapping(value = "/{id}/dossie.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportarDossie(@AuthenticationPrincipal UsuarioAutenticado ator,
                                                 @PathVariable Long id) {
        DossieDemandaService.PdfDossie pdf = service.gerar(ator, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.nomeArquivo(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.conteudo().length)
                .body(pdf.conteudo());
    }
}
