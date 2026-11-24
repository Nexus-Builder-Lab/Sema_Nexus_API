package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.Metadados;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.PontoDetalhe;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.PontosResponse;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.PorMunicipioResponse;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Camada de referência com dados PÚBLICOS do INPE (Issue #34). Somente leitura; qualquer perfil autenticado.
 * Nunca se mistura aos indicadores fictícios do Flux.
 */
@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/intelligence/referencia")
@Tag(name = "Intelligence — Referência de satélite (INPE)")
public class ReferenciaSateliteController {

    private final ReferenciaSateliteService service;

    public ReferenciaSateliteController(ReferenciaSateliteService service) {
        this.service = service;
    }

    @Operation(summary = "Metadados das fontes públicas do INPE: período, data da importação, quantidade e licença")
    @GetMapping("/metadados")
    public Metadados metadados() {
        return service.metadados();
    }

    @Operation(summary = "Pontos de satélite do INPE (focos de calor / alertas DETER); limite padrão 5000, máximo 20000")
    @GetMapping("/pontos")
    public PontosResponse pontos(
            @RequestParam(required = false) TipoReferencia tipo,
            @Parameter(description = "Data inicial (AAAA-MM-DD)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @Parameter(description = "Data final (AAAA-MM-DD)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Integer municipioId,
            @RequestParam(required = false) Bioma bioma,
            @RequestParam(required = false) Integer limite) {
        return service.pontos(new ReferenciaFiltro(tipo, de, ate, municipioId, bioma), limite);
    }

    @Operation(summary = "Detalhe de um ponto de satélite do INPE (painel do mapa): todos os campos, área e polígono",
            description = "Qualquer perfil autenticado (dados públicos). Alertas DETER trazem o polígono GeoJSON e a "
                    + "área informada pela fonte (km² e ha). Focos de calor não têm área: o INPE fornece o foco como "
                    + "ponto, e a resposta diz isso em areaObservacao.")
    @GetMapping("/pontos/{id}")
    public PontoDetalhe detalhe(@PathVariable long id) {
        return service.detalhe(id);
    }

    @Operation(summary = "Contagem de pontos de satélite por município (camada coroplética e cards)")
    @GetMapping("/por-municipio")
    public PorMunicipioResponse porMunicipio(
            @RequestParam(required = false) TipoReferencia tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.porMunicipio(new ReferenciaFiltro(tipo, de, ate, null, null));
    }
}
