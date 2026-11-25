package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import br.com.nexusbuilderlab.semanexus.security.Autorizacao;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/intelligence/sentinela")
@Tag(name = "Intelligence — Sentinela Territorial",
        description = "Anomalias territoriais e operacionais e briefing executivo (Issue #138)")
public class SentinelaController {

    private final SentinelaTerritorialService service;

    public SentinelaController(SentinelaTerritorialService service) {
        this.service = service;
    }

    @Operation(summary = "Briefing executivo da Sentinela Territorial (não grava nada)",
            description = "Perfis: ADMIN, GESTOR. Anomalias por regras determinísticas: `INPE_SEM_DEMANDA` (município "
                    + "com focos + DETER na janela recente do INPE acima do limite e sem demanda aberta; a janela "
                    + "termina no registro mais recente do pacote, que é estático), `FILA_ACUMULADA` (demandas paradas "
                    + "no mesmo status há N dias) e `EQUIPE_SOBRECARREGADA` (ativas/atrasadas por fiscal acima dos "
                    + "limites). Cada anomalia traz severidade, números e ações (`FILTRAR_MAPA`, `FILTRAR_DEMANDAS`, "
                    + "`CRIAR_DEMANDA_MONITORAMENTO` com município, coordenadas do agrupamento mais denso e origem "
                    + "MONITORAMENTO — só pré-preenche; a criação é feita por uma pessoa em `POST /api/demandas`). "
                    + "`resumo` pelo agente `sentinela` (`modo` LOCAL/CLAUDE; números validados contra os dados) ou "
                    + "por template (`modo` DEMONSTRATIVO). Devolve na hora o último briefing (pré-calculado pela "
                    + "rotina `SENTINELA_CRON`); vencido (`SENTINELA_CACHE_MINUTOS`), uma atualização roda em segundo "
                    + "plano — por isso o modelo nunca roda nesta requisição e o endpoint não entra na categoria IA do "
                    + "limite de requisições. Falha de atualização: último briefing com aviso; sem briefing algum, 503 "
                    + "com Retry-After. Issue #138.")
    @ApiResponse(responseCode = "200", description = "Briefing atual",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = BriefingSentinela.class)))
    @ApiResponse(responseCode = "503", description = "Ainda não há briefing e a geração falhou (Retry-After)",
            content = @Content(mediaType = "application/problem+json"))
    @PreAuthorize(Autorizacao.ADMIN_GESTOR)
    @GetMapping("/briefing")
    public BriefingSentinela briefing() {
        return service.briefing();
    }

    /** Sem briefing algum e geração em falha: 503 problem+json com Retry-After (sem detalhe interno). */
    @ExceptionHandler(SentinelaIndisponivelException.class)
    public ResponseEntity<ProblemDetail> indisponivel(SentinelaIndisponivelException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        p.setTitle("Briefing indisponível");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSegundos()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(p);
    }
}
