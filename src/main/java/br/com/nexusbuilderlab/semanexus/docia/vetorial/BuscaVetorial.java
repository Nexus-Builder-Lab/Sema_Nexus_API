package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Parte vetorial da busca híbrida (Issue #141): documentos ativos ordenados pela similaridade de cosseno entre a
 * consulta e o trecho mais próximo de cada um. Em duas etapas — {@link #vetorizar} (chamada ao modelo, fora de
 * transação) e {@link #buscar(float[], int, Set)} (consulta no banco) — para não segurar conexão durante a rede.
 */
public interface BuscaVetorial {

    /**
     * @param similaridade cosseno entre consulta e trecho (−1 a 1; quanto maior, mais próximo no espaço do modelo). É
     *                     proximidade semântica, não confiança de que o documento responde à pergunta.
     */
    record AchadoVetorial(long documentoId, String trecho, double similaridade) {
    }

    /** Embedding da consulta; vazio quando a parte vetorial não pode rodar (desligada, sem modelo, nada indexado). */
    Optional<float[]> vetorizar(String consulta);

    /**
     * @param idsPermitidos não nulo: só esses documentos são pesquisados (ex.: vinculados à demanda)
     * @return vazio em qualquer falha (a busca segue lexical)
     */
    Optional<List<AchadoVetorial>> buscar(float[] consulta, int maxDocumentos, Set<Long> idsPermitidos);
}
