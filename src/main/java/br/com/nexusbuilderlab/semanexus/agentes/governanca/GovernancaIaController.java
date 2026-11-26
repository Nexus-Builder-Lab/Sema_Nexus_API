package br.com.nexusbuilderlab.semanexus.agentes.governanca;

import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Governança de IA", description = "Concordância Humano × IA, feedback do DocIA e bateria de avaliação "
        + "dos agentes (Issue #131)")
public class GovernancaIaController {

    private final BateriaAvaliacaoService bateria;
    private final GovernancaIaService governanca;

    public GovernancaIaController(BateriaAvaliacaoService bateria, GovernancaIaService governanca) {
        this.bateria = bateria;
        this.governanca = governanca;
    }

    @Operation(summary = "Bateria determinística de avaliação dos agentes (somente ADMIN)",
            description = "Roda casos fixos de regressão e adversariais (injeção de instrução no relato, pergunta "
                    + "sobre legislação/multa, histórico forjado, equipe sem fiscal, prazo no passado) sobre a lógica "
                    + "determinística dos agentes de Triagem, Distribuição e DocIA. Não chama modelo de IA e não lê nem "
                    + "grava demandas, documentos ou outros dados operacionais; o resultado é devolvido e a execução "
                    + "fica na auditoria (`AGENTES_AVALIAR`). Issue #131.")
    @ApiResponse(responseCode = "200", description = "Relatório com cada caso, esperado × obtido")
    @ApiResponse(responseCode = "403", description = "Perfil diferente de ADMIN")
    @PreAuthorize(Autorizacao.SOMENTE_ADMIN)
    @PostMapping("/api/sistema/agentes/avaliar")
    public RelatorioAvaliacao avaliar() {
        return bateria.executar();
    }

    @Operation(summary = "Feedback opcional sobre uma resposta do Assistente DocIA",
            description = "Qualquer perfil autenticado, somente para respostas dadas a ele mesmo: `execucaoId` é o "
                    + "campo de mesmo nome de `RespostaAssistente`. `UTIL` ou `IMPRECISA`; pode ser trocado depois. "
                    + "404 se a execução não existe ou é de outro usuário. Auditado (`DOCIA_AVALIAR_RESPOSTA`). "
                    + "Issue #131.")
    @ApiResponse(responseCode = "204", description = "Feedback registrado")
    @ApiResponse(responseCode = "404", description = "Execução inexistente ou de outro usuário")
    @PreAuthorize(Autorizacao.AUTENTICADO)
    @PostMapping("/api/docia/respostas/{execucaoId}/avaliacao")
    public ResponseEntity<Void> avaliarResposta(@AuthenticationPrincipal UsuarioAutenticado ator,
                                                @PathVariable String execucaoId,
                                                @Valid @RequestBody AvaliacaoRespostaRequest pedido) {
        governanca.avaliarResposta(ator, execucaoId, pedido.avaliacao());
        return ResponseEntity.noContent().build();
    }
}
