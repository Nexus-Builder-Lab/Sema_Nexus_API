package br.com.nexusbuilderlab.semanexus.agentes;

/**
 * Callback de eventos da execução de um agente (Issue #135). O padrão {@link #NENHUM} não faz nada — os endpoints
 * JSON e os agentes que não transmitem seguem iguais. {@link #cancelado()} permite ao executor parar entre etapas
 * (antes de cada chamada ao modelo e de cada ferramenta) e desistir da fila da GPU quando o cliente desconecta.
 */
public interface ObservadorAgente {

    ObservadorAgente NENHUM = evento -> { };

    /** Recebe um evento. Falhas aqui nunca interrompem o agente (o executor as ignora). */
    void emitir(EventoAgente evento);

    /**
     * Entrega o evento sem deixar uma falha do observador interromper quem emite (agente, serviço ou fallback).
     */
    static void emitirSeguro(ObservadorAgente observador, EventoAgente evento) {
        if (observador == null) {
            return;
        }
        try {
            observador.emitir(evento);
        } catch (RuntimeException e) {
            // cliente desconectado ou observador com defeito: o evento é descartado
        }
    }

    /** true quando quem acompanha a execução desistiu (ex.: cliente SSE desconectado). */
    default boolean cancelado() {
        return false;
    }
}
