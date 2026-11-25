package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import java.util.List;
import java.util.Optional;

/**
 * Parte vetorial da busca híbrida (Issue #141): documentos ativos ordenados pela similaridade de cosseno entre a
 * consulta e o trecho mais próximo de cada um.
 */
public interface BuscaVetorial {

    /**
     * @param similaridade cosseno entre consulta e trecho (−1 a 1; quanto maior, mais próximo no espaço do modelo). É
     *                     proximidade semântica, não confiança de que o documento responde à pergunta.
     */
    record AchadoVetorial(long documentoId, String trecho, double similaridade) {
    }

    /** Vazio quando a parte vetorial não pôde rodar (desligada, sem modelo, sem trechos indexados ou falha). */
    Optional<List<AchadoVetorial>> buscar(String consulta, int maxDocumentos);
}
