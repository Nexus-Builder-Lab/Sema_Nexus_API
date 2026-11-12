package br.com.nexusbuilderlab.semanexus.intelligence.relatorio;

import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorFiltro;
import br.com.nexusbuilderlab.semanexus.intelligence.relatorio.dto.RelatorioDemandasResponse;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Relatórios de demandas (Issue #28). Perfis ADMIN, GESTOR, ANALISTA e CONSULTA; FISCAL recebe 403. */
@RestController
@PreAuthorize(Autorizacao.RELATORIOS)
@RequestMapping("/api/relatorios")
@Tag(name = "Intelligence — Relatórios", description = "Relatórios de demandas com filtros e exportação CSV (dados simulados)")
public class RelatorioController {

    private final RelatorioService service;

    public RelatorioController(RelatorioService service) {
        this.service = service;
    }

    @Operation(summary = "Relatório de demandas (JSON paginado)",
            description = "Filtros opcionais: de/ate (data de recebimento), municipioId, bioma, categoria, status. "
                    + "Ordenado da mais recente para a mais antiga; size máximo 100.")
    @GetMapping("/demandas")
    public RelatorioDemandasResponse demandas(@ParameterObject IndicadorFiltro filtro,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.pagina(filtro, page, size);
    }

    @Operation(summary = "Relatório de demandas em CSV",
            description = "UTF-8 com BOM, separador ';', primeira linha 'AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS'. "
                    + "Mesmos filtros do JSON, sem paginação.")
    @GetMapping(value = "/demandas.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> demandasCsv(@ParameterObject IndicadorFiltro filtro) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("relatorio-demandas-ficticio.csv").build().toString())
                .body(service.csv(filtro));
    }
}
