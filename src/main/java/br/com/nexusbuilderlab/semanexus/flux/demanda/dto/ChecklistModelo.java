package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Modelo (versionado no código) do checklist de fiscalização de uma categoria (Issue #145). */
@Schema(description = "Modelo do checklist estruturado da fiscalização para uma categoria (versionado no código)")
public record ChecklistModelo(
        CategoriaFiscalizacao categoria,
        @Schema(example = "Desmatamento ilegal") String rotulo,
        @Schema(description = "Versão do modelo", example = "1") int versao,
        List<Item> itens) {

    /** Tipo de resposta de um item. */
    public enum TipoItem { BOOLEANO, TEXTO, NUMERO }

    @Schema(name = "ChecklistItemModelo", description = "Item do checklist")
    public record Item(
            @Schema(description = "Identificador estável do item (chave no JSON enviado)", example = "maquinario_no_local")
            String id,
            @Schema(example = "Havia maquinário no local?") String rotulo,
            TipoItem tipo,
            @Schema(description = "Item obrigatório") boolean obrigatorio) {
    }
}
