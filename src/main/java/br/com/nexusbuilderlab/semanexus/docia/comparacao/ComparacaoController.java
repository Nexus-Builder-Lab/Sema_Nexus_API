package br.com.nexusbuilderlab.semanexus.docia.comparacao;

import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ComparacaoRequest;
import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ComparacaoResponse;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/documentos")
@Tag(name = "DocIA — Comparação", description = "Comparação determinística de dois documentos")
public class ComparacaoController {

    private final ComparacaoService service;

    public ComparacaoController(ComparacaoService service) {
        this.service = service;
    }

    @Operation(summary = "Compara dois documentos",
            description = "Qualquer perfil autenticado. Retorna metadados iguais/diferentes, diff por linhas e "
                    + "palavras (LCS), trechos semelhantes, termos exclusivos e similaridade. Determinístico, não é "
                    + "IA (`geradoPorIa=false`). Documento inexistente → 404; sem texto extraído → 400 com a "
                    + "mensagem indicando o documento.")
    @PostMapping("/comparacao")
    public ComparacaoResponse comparar(@Valid @RequestBody ComparacaoRequest pedido) {
        return service.comparar(pedido.aId(), pedido.bId());
    }
}
