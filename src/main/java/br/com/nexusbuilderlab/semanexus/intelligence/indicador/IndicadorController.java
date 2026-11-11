package br.com.nexusbuilderlab.semanexus.intelligence.indicador;

import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.ContagemItem;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.ContagemMunicipio;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorFiltro;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorLista;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.ResumoIndicadores;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.SerieMensalItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Indicadores agregados do SEMA Intelligence (Issue #17). Qualquer perfil
 * autenticado consulta. Toda resposta carrega {@code demonstrativo: true}: os números são calculados sobre
 * dados fictícios.
 */
@RestController
@RequestMapping("/api/intelligence")
@Tag(name = "Intelligence — Indicadores", description = "Indicadores ambientais agregados (dados simulados)")
public class IndicadorController {

    private final IndicadorService service;

    public IndicadorController(IndicadorService service) {
        this.service = service;
    }

    @Operation(summary = "Totais gerais: demandas, em andamento, finalizadas, atrasadas e documentos")
    @GetMapping("/resumo")
    public ResumoIndicadores resumo(@ParameterObject IndicadorFiltro filtro) {
        return service.resumo(filtro);
    }

    @Operation(summary = "Série mensal de demandas cadastradas e finalizadas",
            description = "Meses em ordem cronológica; meses sem demandas aparecem com zero dentro do intervalo filtrado.")
    @GetMapping("/serie-mensal")
    public IndicadorLista<SerieMensalItem> serieMensal(@ParameterObject IndicadorFiltro filtro) {
        return service.serieMensal(filtro);
    }

    @Operation(summary = "Demandas por município (somente municípios com demandas), do maior para o menor total")
    @GetMapping("/por-municipio")
    public IndicadorLista<ContagemMunicipio> porMunicipio(@ParameterObject IndicadorFiltro filtro) {
        return service.porMunicipio(filtro);
    }

    @Operation(summary = "Demandas por bioma (Amazônia, Cerrado, Pantanal)")
    @GetMapping("/por-bioma")
    public IndicadorLista<ContagemItem> porBioma(@ParameterObject IndicadorFiltro filtro) {
        return service.porBioma(filtro);
    }

    @Operation(summary = "Demandas por categoria ambiental")
    @GetMapping("/por-categoria")
    public IndicadorLista<ContagemItem> porCategoria(@ParameterObject IndicadorFiltro filtro) {
        return service.porCategoria(filtro);
    }

    @Operation(summary = "Demandas por situação (os 7 status, na ordem do fluxo, incluindo zeros)")
    @GetMapping("/por-situacao")
    public IndicadorLista<ContagemItem> porSituacao(@ParameterObject IndicadorFiltro filtro) {
        return service.porSituacao(filtro);
    }
}
