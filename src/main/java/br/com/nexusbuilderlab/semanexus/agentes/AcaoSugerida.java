package br.com.nexusbuilderlab.semanexus.agentes;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Action Card (Issue #136): ação que o agente sugere e que só uma pessoa confirma na tela. Montada pela API a partir
 * do que o agente de fato usou; nunca é executada pelo servidor durante a execução do agente.
 */
@Schema(description = "Ação sugerida pelo agente, derivada no servidor e validada (existência e acesso do usuário). "
        + "Nada é gravado ao sugerir: o cliente abre a tela correspondente e a pessoa confirma.")
public record AcaoSugerida(
        @Schema(description = "ABRIR_COMPARACAO {aId, bId}; SUGERIR_VINCULO_DEMANDA {documentoId, demandaId}; "
                + "VER_NO_MAPA {lat, lon, zoom?, demandaId?}; ABRIR_DEMANDA {demandaId}", example = "ABRIR_DEMANDA")
        Tipo tipo,
        @Schema(description = "Texto do botão, montado pela API", example = "Abrir demanda DEM-2026-0042") String rotulo,
        @Schema(description = "Parâmetros do tipo (números); chaves conforme `tipo`",
                example = "{\"demandaId\": 42}") Map<String, Object> parametros) {

    @Schema(name = "TipoAcaoSugerida")
    public enum Tipo {
        ABRIR_COMPARACAO, SUGERIR_VINCULO_DEMANDA, VER_NO_MAPA, ABRIR_DEMANDA
    }
}
