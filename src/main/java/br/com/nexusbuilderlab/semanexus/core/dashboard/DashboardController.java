package br.com.nexusbuilderlab.semanexus.core.dashboard;

import br.com.nexusbuilderlab.semanexus.common.exception.ContratoPendenteException;
import br.com.nexusbuilderlab.semanexus.common.openapi.ContratoPendente;
import br.com.nexusbuilderlab.semanexus.core.dashboard.dto.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contrato do dashboard inicial (implementação na Issue #17). Qualquer perfil autenticado consulta. */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Visão inicial: totais, situação das demandas e atividades recentes (dados simulados)")
public class DashboardController {

    @Operation(summary = "Dashboard inicial")
    @GetMapping
    @ContratoPendente(issue = 17)
    public DashboardResponse dashboard() {
        throw new ContratoPendenteException(17);
    }
}
