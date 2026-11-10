package br.com.nexusbuilderlab.semanexus.intelligence.indicador;

import br.com.nexusbuilderlab.semanexus.common.exception.ContratoPendenteException;
import br.com.nexusbuilderlab.semanexus.common.openapi.ContratoPendente;
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
 * Contrato dos indicadores agregados do SEMA Intelligence (implementação na Issue #17). Qualquer perfil
 * autenticado consulta. Toda resposta carrega {@code demonstrativo: true}: os números são calculados sobre
 * dados fictícios.
 */
@RestController
@RequestMapping("/api/intelligence")
@Tag(name = "Intelligence — Indicadores", description = "Indicadores ambientais agregados (dados simulados)")
public class IndicadorController {

    @Operation(summary = "Totais gerais: demandas, em andamento, finalizadas, atrasadas e documentos")
    @GetMapping("/resumo")
    @ContratoPendente(issue = 17)
    public ResumoIndicadores resumo(@ParameterObject IndicadorFiltro filtro) {
        throw new ContratoPendenteException(17);
    }

    @Operation(summary = "Série mensal de demandas cadastradas e finalizadas",
            description = "Meses em ordem cronológica; meses sem demandas aparecem com zero dentro do intervalo filtrado.")
    @GetMapping("/serie-mensal")
    @ContratoPendente(issue = 17)
    public IndicadorLista<SerieMensalItem> serieMensal(@ParameterObject IndicadorFiltro filtro) {
        throw new ContratoPendenteException(17);
    }

    @Operation(summary = "Demandas por município (somente municípios com demandas), do maior para o menor total")
    @GetMapping("/por-municipio")
    @ContratoPendente(issue = 17)
    public IndicadorLista<ContagemMunicipio> porMunicipio(@ParameterObject IndicadorFiltro filtro) {
        throw new ContratoPendenteException(17);
    }

    @Operation(summary = "Demandas por bioma (Amazônia, Cerrado, Pantanal)")
    @GetMapping("/por-bioma")
    @ContratoPendente(issue = 17)
    public IndicadorLista<ContagemItem> porBioma(@ParameterObject IndicadorFiltro filtro) {
        throw new ContratoPendenteException(17);
    }

    @Operation(summary = "Demandas por categoria ambiental")
    @GetMapping("/por-categoria")
    @ContratoPendente(issue = 17)
    public IndicadorLista<ContagemItem> porCategoria(@ParameterObject IndicadorFiltro filtro) {
        throw new ContratoPendenteException(17);
    }

    @Operation(summary = "Demandas por situação (os 7 status, na ordem do fluxo, incluindo zeros)")
    @GetMapping("/por-situacao")
    @ContratoPendente(issue = 17)
    public IndicadorLista<ContagemItem> porSituacao(@ParameterObject IndicadorFiltro filtro) {
        throw new ContratoPendenteException(17);
    }
}
