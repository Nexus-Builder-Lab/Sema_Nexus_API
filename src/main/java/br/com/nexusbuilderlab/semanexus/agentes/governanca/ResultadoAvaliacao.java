package br.com.nexusbuilderlab.semanexus.agentes.governanca;

/** Resultado de uma linha de {@code AVALIACAO_AGENTE} (Issue #131). */
public enum ResultadoAvaliacao {
    /** Sugestão registrada, ainda sem decisão humana. */
    PENDENTE,
    /** A pessoa aplicou exatamente o que o agente sugeriu. */
    ACEITA_INTEGRAL,
    /** A pessoa aproveitou parte da sugestão e ajustou o resto. */
    AJUSTADA_PARCIAL,
    /** A pessoa decidiu outra coisa (decisão diferente ou nenhum campo aproveitado). */
    DESCARTADA,
    /** Feedback do autor da pergunta: a resposta do DocIA foi útil. */
    UTIL,
    /** Feedback do autor da pergunta: a resposta do DocIA foi imprecisa. */
    IMPRECISA
}
