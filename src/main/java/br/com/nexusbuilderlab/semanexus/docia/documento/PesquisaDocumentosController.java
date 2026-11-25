package br.com.nexusbuilderlab.semanexus.docia.documento;

import br.com.nexusbuilderlab.semanexus.docia.documento.dto.PesquisaDocumentos;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Pesquisa ranqueada da biblioteca, híbrida quando há embeddings locais (Issue #141). Só consulta. */
@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/documentos/busca")
@Tag(name = "DocIA — Documentos", description = "Upload, biblioteca documental, detalhes, texto extraído e download")
public class PesquisaDocumentosController {

    private final PesquisaDocumentosService service;

    public PesquisaDocumentosController(PesquisaDocumentosService service) {
        this.service = service;
    }

    @Operation(summary = "Pesquisa ranqueada: busca textual + vetorial (híbrida) com fusão RRF",
            description = "Qualquer perfil autenticado. Mesma recuperação do Assistente DocIA: busca textual por "
                    + "termos/radicais e, quando o modelo local de embeddings (nomic-embed-text via Ollama) está "
                    + "disponível, busca vetorial no Oracle 23ai (VECTOR_DISTANCE, cosseno), fundidas por RRF. "
                    + "`recuperacao` informa o modo usado (LEXICAL | HIBRIDA); sem o modelo a pesquisa segue lexical, "
                    + "sem erro. `aderenciaTextual` e `similaridadeSemantica` medem proximidade com a consulta, não "
                    + "confiança. Lista vazia = nada atende à consulta.")
    @ApiResponse(responseCode = "200", description = "Documentos do mais ao menos relevante")
    @ApiResponse(responseCode = "400", description = "Consulta, limite ou modo inválidos (problem+json)")
    @GetMapping
    public PesquisaDocumentos pesquisar(
            @Parameter(description = "Consulta (2 a 200 caracteres)", required = true, example = "área desmatada em Colniza")
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "Máximo de documentos (1 a 20; padrão 10)", schema = @Schema(minimum = "1",
                    maximum = "20", defaultValue = "10"))
            @RequestParam(name = "limite", required = false) Integer limite,
            @Parameter(description = "AUTO (padrão: híbrida quando disponível) ou LEXICAL (força só a busca "
                    + "textual, para comparação)", schema = @Schema(allowableValues = {"AUTO", "LEXICAL"},
                    defaultValue = "AUTO"))
            @RequestParam(name = "modo", required = false) String modo) {
        return service.pesquisar(q, limite, modo);
    }
}
