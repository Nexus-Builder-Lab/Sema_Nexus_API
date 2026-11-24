package br.com.nexusbuilderlab.semanexus.flux.agente;

import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demandas")
@Tag(name = "Agentes de IA", description = "Agentes que rodam dentro do sistema e sugerem ações (Issue #106)")
public class AgenteMinutaController {

    private final AgenteMinutaService service;

    public AgenteMinutaController(AgenteMinutaService service) {
        this.service = service;
    }

    @Operation(summary = "Minuta de relatório/parecer de vistoria (Agente Copiloto de Minuta; não grava nada)",
            description = "Perfis: ADMIN, GESTOR, ANALISTA e o FISCAL designado para a demanda (outro fiscal: 403; "
                    + "CONSULTA: 403). O agente consulta, só para leitura, o dossiê da demanda, o registro de "
                    + "fiscalização, os documentos vinculados e os dados públicos do INPE no município, e devolve "
                    + "uma minuta em Markdown (identificação, histórico, constatações da fiscalização, documentos "
                    + "considerados, contexto INPE, conclusões/recomendações preliminares) sempre encabeçada por "
                    + "\"" + AgenteMinutaService.AVISO + "\". Números escritos pela IA são conferidos com os dados "
                    + "das ferramentas; se não conferirem, ou sem IA, a minuta vem do template determinístico "
                    + "(`modo` DEMONSTRATIVO). Nada é gravado: a pessoa revisa e, se quiser, envia o texto pelo "
                    + "`POST /api/documentos` vinculando à demanda. Limite de requisições da categoria IA (429). "
                    + "Issue #137.")
    @ApiResponse(responseCode = "200", description = "Minuta gerada (não gravada)")
    @ApiResponse(responseCode = "403", description = "Perfil CONSULTA ou fiscal que não é o designado")
    @ApiResponse(responseCode = "404", description = "Demanda inexistente")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR_ANALISTA_FISCAL)
    @PostMapping("/{id}/minuta-vistoria")
    public MinutaVistoria gerar(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id) {
        return service.gerar(ator, id);
    }
}
