package br.com.nexusbuilderlab.semanexus.agentes;

import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Evento de uma execução de agente transmitido por SSE (Issue #135). {@code tipo} é o nome do evento SSE e
 * {@code dados} vira o JSON do campo {@code data}. Os dados nunca trazem detalhe técnico interno: só nomes seguros de
 * ferramenta, os resumos já exibidos nos passos (com erros do catálogo {@link ErroFerramenta}) e mensagens públicas.
 */
@Schema(name = "EventoAgente", description = "Evento SSE de um agente: `tipo` é o nome do evento (linha `event:`) e "
        + "`dados` o JSON da linha `data:`. Ordem: inicio → (ferramenta_iniciada → ferramenta_concluida)* → "
        + "fallback_demonstrativo? → delta_texto? → concluido | erro.")
public record EventoAgente(
        @Schema(description = "Nome do evento SSE", allowableValues = {"inicio", "ferramenta_iniciada",
                "ferramenta_concluida", "fallback_demonstrativo", "delta_texto", "concluido", "erro"},
                example = "ferramenta_concluida") String tipo,
        @Schema(description = "inicio {agente}; ferramenta_iniciada {ferramenta, ordem}; ferramenta_concluida "
                + "{ferramenta, ordem, resumo, erro}; fallback_demonstrativo {motivo}; delta_texto {texto}; concluido "
                + "= mesmo JSON do endpoint síncrono; erro {mensagem, referencia}") Map<String, Object> dados) {

    public static final String INICIO = "inicio";
    public static final String FERRAMENTA_INICIADA = "ferramenta_iniciada";
    public static final String FERRAMENTA_CONCLUIDA = "ferramenta_concluida";
    public static final String DELTA_TEXTO = "delta_texto";
    public static final String CONCLUIDO = "concluido";
    public static final String FALLBACK_DEMONSTRATIVO = "fallback_demonstrativo";
    public static final String ERRO = "erro";

    public static EventoAgente inicio(String agente) {
        return new EventoAgente(INICIO, Map.of("agente", agente));
    }

    public static EventoAgente ferramentaIniciada(String ferramenta, int ordem) {
        return new EventoAgente(FERRAMENTA_INICIADA, mapa("ferramenta", ferramenta, "ordem", ordem));
    }

    public static EventoAgente ferramentaConcluida(String ferramenta, int ordem, String resumo, boolean erro) {
        Map<String, Object> m = mapa("ferramenta", ferramenta, "ordem", ordem);
        m.put("resumo", resumo == null ? "" : resumo);
        m.put("erro", erro);
        return new EventoAgente(FERRAMENTA_CONCLUIDA, m);
    }

    public static EventoAgente deltaTexto(String texto) {
        return new EventoAgente(DELTA_TEXTO, Map.of("texto", texto == null ? "" : texto));
    }

    /** {@code motivo}: mensagem pública fixa (nunca a mensagem da exceção). */
    public static EventoAgente fallbackDemonstrativo(String motivo) {
        return new EventoAgente(FALLBACK_DEMONSTRATIVO, Map.of("motivo", motivo));
    }

    private static Map<String, Object> mapa(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }
}
