package br.com.nexusbuilderlab.semanexus.docia.comparacao;

import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ComparacaoRequest;
import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ComparacaoResponse;
import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ExplicacaoComparacaoResponse;
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
@RequestMapping
@Tag(name = "DocIA — Comparação", description = "Comparação determinística de dois documentos")
public class ComparacaoController {

    private final ComparacaoService service;
    private final ExplicacaoComparacaoService explicacao;

    public ComparacaoController(ComparacaoService service, ExplicacaoComparacaoService explicacao) {
        this.explicacao = explicacao;
        this.service = service;
    }

    @Operation(summary = "Compara dois documentos",
            description = "Qualquer perfil autenticado. Retorna metadados iguais/diferentes, diff por linhas e "
                    + "palavras (LCS), trechos semelhantes, termos exclusivos e similaridade. Determinístico, não é "
                    + "IA (`geradoPorIa=false`). Documento inexistente → 404; sem texto extraído → 400 com a "
                    + "mensagem indicando o documento.")
    @PostMapping("/api/documentos/comparacao")
    public ComparacaoResponse comparar(@Valid @RequestBody ComparacaoRequest pedido) {
        return service.comparar(pedido.aId(), pedido.bId());
    }

    @Operation(summary = "Explica as diferenças entre dois documentos",
            description = "Qualquer perfil autenticado (mesma autorização da comparação). Tabela de grandezas "
                    + "alteradas (áreas em ha, vazões, valores R$, datas, prazos, percentuais e conclusões) extraída "
                    + "de forma determinística, mais resumo curto escrito pelo agente local (`modo` LOCAL/CLAUDE) ou "
                    + "por regras (`modo` DEMONSTRATIVO). Os números nunca vêm do modelo; nada é gravado. "
                    + "Documento inexistente → 404; sem texto extraído → 400.")
    @PostMapping("/api/docia/comparacao/explicar")
    public ExplicacaoComparacaoResponse explicar(@Valid @RequestBody ComparacaoRequest pedido) {
        return explicacao.explicar(pedido.aId(), pedido.bId());
    }
}
