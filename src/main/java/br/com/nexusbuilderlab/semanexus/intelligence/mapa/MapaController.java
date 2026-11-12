package br.com.nexusbuilderlab.semanexus.intelligence.mapa;

import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import org.springframework.security.access.prepost.PreAuthorize;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.IndicadorService;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorFiltro;
import br.com.nexusbuilderlab.semanexus.intelligence.mapa.dto.MapaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pontos do mapa (Issue #17). Qualquer perfil autenticado consulta. */
@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/intelligence")
@Tag(name = "Intelligence — Indicadores")
public class MapaController {

    private final IndicadorService service;

    public MapaController(IndicadorService service) {
        this.service = service;
    }

    @Operation(summary = "Pontos do mapa: demandas com localização, filtráveis por período, município, bioma, categoria, status e setor")
    @GetMapping("/mapa")
    public MapaResponse mapa(@ParameterObject IndicadorFiltro filtro) {
        return service.mapa(filtro);
    }
}
