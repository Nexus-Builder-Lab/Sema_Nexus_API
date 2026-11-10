package br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Envelope das séries e contagens: carrega o marcador de dado simulado junto com os itens. */
@Schema(description = "Itens de um indicador, com o marcador de dados simulados")
public record IndicadorLista<T>(
        @Schema(description = "Sempre true: indicadores calculados sobre dados fictícios", example = "true") boolean demonstrativo,
        @Schema(example = "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS") String aviso,
        List<T> itens) {
}
