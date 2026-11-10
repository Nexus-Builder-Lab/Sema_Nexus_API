package br.com.nexusbuilderlab.semanexus.intelligence.mapa;

import br.com.nexusbuilderlab.semanexus.common.exception.ContratoPendenteException;
import br.com.nexusbuilderlab.semanexus.common.openapi.ContratoPendente;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorFiltro;
import br.com.nexusbuilderlab.semanexus.intelligence.mapa.dto.MapaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contrato dos pontos do mapa (implementação na Issue #17). Qualquer perfil autenticado consulta. */
@RestController
@RequestMapping("/api/intelligence")
@Tag(name = "Intelligence — Indicadores")
public class MapaController {

    @Operation(summary = "Pontos do mapa: demandas com localização, filtráveis por período, município, bioma, categoria e status")
    @GetMapping("/mapa")
    @ContratoPendente(issue = 17)
    public MapaResponse mapa(@ParameterObject IndicadorFiltro filtro) {
        throw new ContratoPendenteException(17);
    }
}
