package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import br.com.nexusbuilderlab.semanexus.security.Autorizacao;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
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
                    + "por template (`modo` DEMONSTRATIVO). Servido do cache (`SENTINELA_CACHE_MINUTOS`), "
                    + "pré-calculado pela rotina agendada (`SENTINELA_CRON`). Issue #138.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR)
    @GetMapping("/briefing")
    public BriefingSentinela briefing() {
        return service.briefing();
    }
}
