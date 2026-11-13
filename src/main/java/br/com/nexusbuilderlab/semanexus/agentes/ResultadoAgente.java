package br.com.nexusbuilderlab.semanexus.agentes;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** Resposta final do agente (JSON no esquema pedido), passos executados e consumo de tokens. */
public record ResultadoAgente(JsonNode resposta, List<PassoAgente> passos, String modelo, long tokensEntrada,
                              long tokensSaida, long tokensCache) {

    /** Ferramenta chamada pelo agente e o resumo do que ela devolveu. */
    public record PassoAgente(String ferramenta, String resumo) {
    }
}
