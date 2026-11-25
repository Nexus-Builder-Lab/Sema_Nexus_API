package br.com.nexusbuilderlab.semanexus.agentes.global;

import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/agentes")
@Tag(name = "Agentes de IA", description = "Agentes que rodam dentro do sistema e sugerem ações (Issue #106)")
public class AgenteGlobalController {

    private final AgenteGlobalService service;

    public AgenteGlobalController(AgenteGlobalService service) {
        this.service = service;
    }

    @Operation(summary = "Assistente Global (Ctrl+K): pergunta rápida sobre o sistema (somente leitura)",
            description = "Qualquer perfil autenticado. O agente `assistente-global` consulta, só para leitura e com o "
                    + "escopo do usuário logado, as ferramentas `resumo_indicadores_ambientais`, `prazos_criticos`, "
                    + "`buscar_demandas`, `buscar_documentos` e `resumo_alertas_inpe` (o FISCAL só vê demandas da "
                    + "sua equipe ou atribuídas a ele). Devolve uma resposta curta e `links` montados pela API a "
                    + "partir dos registros que as ferramentas devolveram (`/flux/demandas/{id}`, "
                    + "`/docia/documentos/{id}` e telas filtradas) — nunca ids escritos pelo modelo. Números do "
                    + "texto da IA são conferidos com os dados das ferramentas; se não conferirem, a resposta é o "
                    + "resumo determinístico das consultas. Sem IA, modo `DEMONSTRATIVO` (roteamento por "
                    + "palavras-chave), identificado. Nada é gravado. Limite de requisições da categoria IA (429). "
                    + "Issue #139.")
    @ApiResponse(responseCode = "200", description = "Resposta gerada (nada gravado)")
    @PostMapping("/assistente-global")
    public RespostaAssistenteGlobal perguntar(@AuthenticationPrincipal UsuarioAutenticado ator,
                                              @Valid @RequestBody RespostaAssistenteGlobal.Pergunta pedido) {
        return service.perguntar(ator, pedido.pergunta());
    }
}
