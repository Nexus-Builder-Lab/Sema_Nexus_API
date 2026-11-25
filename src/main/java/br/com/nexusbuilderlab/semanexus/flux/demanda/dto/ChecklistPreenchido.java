package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Checklist preenchido e validado no registro da fiscalização (Issue #145). */
@Schema(description = "Checklist estruturado preenchido (validado contra o modelo da categoria da demanda)")
public record ChecklistPreenchido(
        @Schema(description = "Versão do modelo usada na validação", example = "1") int versao,
        CategoriaFiscalizacao categoria,
        @Schema(description = "Respostas por id do item (booleano, texto ou número)",
                example = "{\"local_acessivel\": true, \"area_estimada_ha\": 12.5}")
        Map<String, Object> itens) {
}
