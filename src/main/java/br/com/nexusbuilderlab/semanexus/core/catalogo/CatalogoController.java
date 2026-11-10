package br.com.nexusbuilderlab.semanexus.core.catalogo;

import br.com.nexusbuilderlab.semanexus.core.catalogo.dto.ItemCatalogo;
import br.com.nexusbuilderlab.semanexus.core.catalogo.dto.MunicipioDto;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Catálogos de domínio (leitura, qualquer usuário autenticado). Sem paginação: listas curtas e estáveis. */
@RestController
@RequestMapping("/api/catalogos")
@Tag(name = "Catálogos", description = "Municípios de MT, biomas, categorias, origens, prioridades e status das demandas")
public class CatalogoController {

    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    @Operation(summary = "Municípios de Mato Grosso (142), em ordem alfabética",
            description = "Referência geográfica pública do IBGE. Filtro opcional por bioma predominante.")
    @GetMapping("/municipios")
    public List<MunicipioDto> municipios(
            @Parameter(description = "Filtra pelo bioma predominante do município") @RequestParam(required = false) Bioma bioma) {
        return service.municipios(bioma);
    }

    @Operation(summary = "Biomas de Mato Grosso (Amazônia, Cerrado, Pantanal)")
    @GetMapping("/biomas")
    public List<ItemCatalogo> biomas() {
        return service.biomas();
    }

    @Operation(summary = "Categorias ambientais de fiscalização")
    @GetMapping("/categorias")
    public List<ItemCatalogo> categorias() {
        return service.categorias();
    }

    @Operation(summary = "Origens da demanda")
    @GetMapping("/origens")
    public List<ItemCatalogo> origens() {
        return service.origens();
    }

    @Operation(summary = "Prioridades, da menor para a maior")
    @GetMapping("/prioridades")
    public List<ItemCatalogo> prioridades() {
        return service.prioridades();
    }

    @Operation(summary = "Os 7 status do fluxo da demanda, na ordem do fluxo",
            description = "\"Atrasada\" não é status: é derivada (prazo < hoje e status ≠ FINALIZADA).")
    @GetMapping("/status")
    public List<ItemCatalogo> status() {
        return service.status();
    }
}
