package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ProdutividadeResposta;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Produtividade do Flux (Issue #84). Escopo por perfil igual ao da listagem de demandas. */
@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/flux/produtividade")
@Tag(name = "Flux — Produtividade", description = "Indicadores de produtividade (dados fictícios: demonstrativo=true)")
public class ProdutividadeController {

    private final ProdutividadeService service;

    public ProdutividadeController(ProdutividadeService service) {
        this.service = service;
    }

    @Operation(summary = "KPIs, situação e tempo médio por setor, histórico mensal e produtividade por responsável",
            description = "Recorte por data de recebimento (`de`/`ate`, inclusivos) e setor da equipe designada. "
                    + "FISCAL enxerga apenas as demandas suas ou da sua equipe.")
    @GetMapping
    public ProdutividadeResposta produtividade(
            @AuthenticationPrincipal UsuarioAutenticado ator,
            @Parameter(description = "Recebidas a partir de (AAAA-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @Parameter(description = "Recebidas até (AAAA-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @Parameter(description = "Setor da equipe designada") @RequestParam(required = false) Long setorId) {
        return service.calcular(ator, de, ate, setorId);
    }
}
