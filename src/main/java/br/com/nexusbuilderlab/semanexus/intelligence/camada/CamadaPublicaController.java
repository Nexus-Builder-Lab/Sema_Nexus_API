package br.com.nexusbuilderlab.semanexus.intelligence.camada;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.GlobalExceptionHandler.ErroCampo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.GeoJsonResponse;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.Metadados;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.SobreposicaoCamadasPublicas;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints somente leitura das camadas públicas abertas offline de MT (UCs MMA/CNUC, Terras Indígenas FUNAI e
 * hidrografia principal ANA/SNIRH) e consulta de sobreposição (Issue #144). Qualquer perfil autenticado; respeita
 * o escopo do FISCAL quando consultado por {@code demandaId}.
 */
@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/intelligence/referencia/camadas")
@Tag(name = "Intelligence — Camadas públicas (MT)",
        description = "Camadas públicas abertas offline (UCs, Terras Indígenas e hidrografia principal de MT) com "
                + "fonte, licença, data e oficialSema=false")
public class CamadaPublicaController {

    private final CamadaPublicaService service;
    private final DemandaService demandas;

    public CamadaPublicaController(CamadaPublicaService service, DemandaService demandas) {
        this.service = service;
        this.demandas = demandas;
    }

    @Operation(summary = "Metadados das camadas públicas abertas offline de MT (fonte, licença, data e oficialSema=false)")
    @GetMapping("/metadados")
    public Metadados metadados() {
        return service.metadados();
    }

    @Operation(summary = "Feições GeoJSON de uma camada pública offline (UCs, Terras Indígenas ou Hidrografia de MT)",
            description = "Qualquer perfil autenticado. Filtros opcionais: esfera (ex.: Federal, Estadual, Municipal), "
                    + "nivel (1..3, apenas para HIDROGRAFIA), q (trecho do nome) e limite (padrão 5000, máximo 10000).")
    @GetMapping("/geojson")
    public GeoJsonResponse geojson(
            @Parameter(description = "Camada pública", required = true) @RequestParam TipoCamadaPublica camada,
            @RequestParam(required = false) String esfera,
            @RequestParam(required = false) Integer nivel,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limite) {
        return service.geojson(camada, esfera, nivel, q, limite);
    }

    @Operation(summary = "Consulta de sobreposição e proximidade com UCs, Terras Indígenas e hidrografia principal de MT",
            description = "Informe latitude e longitude (WGS84) OU demandaId (valida acesso/escopo da demanda — FISCAL só "
                    + "acessa demandas da sua equipe ou atribuídas). raioKm padrão 10 km, máximo 50 km.")
    @ApiResponse(responseCode = "200", description = "Análise de sobreposição e proximidade com camadas públicas (oficialSema=false)")
    @ApiResponse(responseCode = "403", description = "Fiscal fora do escopo ao consultar por demandaId")
    @ApiResponse(responseCode = "404", description = "Demanda não encontrada ao consultar por demandaId")
    @GetMapping("/sobreposicao")
    public SobreposicaoCamadasPublicas sobreposicao(
            @AuthenticationPrincipal UsuarioAutenticado ator,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double raioKm,
            @RequestParam(required = false) Long demandaId) {
        List<ErroCampo> erros = new ArrayList<>();
        if (raioKm != null && (Double.isNaN(raioKm) || Double.isInfinite(raioKm)
                || raioKm <= 0 || raioKm > CamadaPublicaService.RAIO_MAXIMO_KM)) {
            erros.add(new ErroCampo("raioKm",
                    "O raio deve ser maior que 0 e de no máximo " + (int) CamadaPublicaService.RAIO_MAXIMO_KM + " km."));
        }
        if ((latitude == null) != (longitude == null)) {
            erros.add(new ErroCampo(latitude == null ? "latitude" : "longitude",
                    "Informe latitude e longitude juntas."));
        } else if (latitude != null) {
            if (Double.isNaN(latitude) || Double.isInfinite(latitude) || latitude < -90 || latitude > 90) {
                erros.add(new ErroCampo("latitude", "Latitude inválida (deve estar entre -90 e 90)."));
            }
            if (Double.isNaN(longitude) || Double.isInfinite(longitude) || longitude < -180 || longitude > 180) {
                erros.add(new ErroCampo("longitude", "Longitude inválida (deve estar entre -180 e 180)."));
            }
        }
        if (demandaId != null && (latitude != null || longitude != null)) {
            erros.add(new ErroCampo("demandaId", "Informe apenas latitude/longitude ou demandaId."));
        }
        if (demandaId == null && latitude == null && longitude == null) {
            erros.add(new ErroCampo("latitude", "Informe latitude e longitude ou demandaId."));
        }
        if (!erros.isEmpty()) {
            throw new DadosInvalidosException(erros);
        }
        if (demandaId != null) {
            DemandaDetalhe d = demandas.detalhar(ator, demandaId);
            return service.sobreposicao(d, raioKm);
        }
        return service.sobreposicaoPonto(latitude, longitude, raioKm);
    }
}
