package br.com.nexusbuilderlab.semanexus.agentes;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Ferramenta que o agente pode chamar. Roda na thread da requisição, portanto com as permissões e o escopo do usuário
 * logado (os Services aplicam as mesmas regras dos endpoints). Ferramentas só consultam: nunca gravam.
 *
 * @param propriedades esquema JSON de cada parâmetro (ex.: {@code Map.of("type", "string", "description", "...")})
 * @param executar     recebe a entrada do modelo e devolve os dados (serializados em JSON) e um resumo legível
 */
public record FerramentaAgente(String nome, String descricao, Map<String, Map<String, Object>> propriedades,
                               List<String> obrigatorias, Function<JsonNode, Resultado> executar) {

    /** Dados devolvidos ao modelo e resumo para a interface ("Como o agente chegou à resposta"). */
    public record Resultado(Object dados, String resumo) {
    }
}
