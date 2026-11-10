package br.com.nexusbuilderlab.semanexus.core.catalogo.dto;

import br.com.nexusbuilderlab.semanexus.core.catalogo.Catalogavel;
import io.swagger.v3.oas.annotations.media.Schema;

/** Item de catálogo: código estável (usado nas requisições), rótulo e posição na ordem natural. */
@Schema(description = "Item de catálogo (enum) da API")
public record ItemCatalogo(
        @Schema(description = "Código estável, usado em filtros e corpos de requisição", example = "EM_TRIAGEM") String codigo,
        @Schema(description = "Rótulo para exibição", example = "Em triagem") String rotulo,
        @Schema(description = "Posição na ordem natural do catálogo (a partir de 1)", example = "2") int ordem) {

    public static ItemCatalogo de(Catalogavel valor, int ordem) {
        return new ItemCatalogo(valor.name(), valor.rotulo(), ordem);
    }
}
