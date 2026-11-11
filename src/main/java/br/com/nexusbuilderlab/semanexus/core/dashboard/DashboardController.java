package br.com.nexusbuilderlab.semanexus.core.dashboard;

import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import org.springframework.security.access.prepost.PreAuthorize;
import br.com.nexusbuilderlab.semanexus.core.dashboard.dto.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard inicial (Issue #17). Qualquer perfil autenticado consulta. */
@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Visão inicial: totais, situação das demandas e atividades recentes (dados simulados)")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @Operation(summary = "Dashboard inicial")
    @GetMapping
    public DashboardResponse dashboard() {
        return service.dashboard();
    }
}
