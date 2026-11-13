package br.com.nexusbuilderlab.semanexus.agentes;

import java.util.List;
import java.util.Map;

/**
 * Definição de um agente: modelo sugerido (o provedor local pode substituí-lo pelo seu modelo), instruções (prompt de
 * sistema, estável), ferramentas e o esquema JSON da resposta final. {@code esforco}: {@code low} ou {@code medium}.
 * {@code maxRodadasFerramentas}: depois dessas rodadas o modelo recebe o pedido sem ferramentas e com o esquema
 * obrigatório (evita uma resposta intermediária em texto livre nos modelos locais).
 */
public record DefinicaoAgente(String nome, String modelo, String instrucoes, List<FerramentaAgente> ferramentas,
                              Map<String, Object> esquemaResposta, String esforco, long maxTokens,
                              int maxRodadasFerramentas) {
}
