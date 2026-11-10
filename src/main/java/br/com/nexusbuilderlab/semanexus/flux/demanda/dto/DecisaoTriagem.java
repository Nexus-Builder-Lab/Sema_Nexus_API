package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

/** Resultado da triagem de uma demanda. */
public enum DecisaoTriagem {
    /** Em triagem → Aguardando distribuição (exige categoria, prioridade e justificativa). */
    CLASSIFICAR,
    /** Em triagem → Pendente (exige justificativa do que falta). */
    SOLICITAR_COMPLEMENTACAO
}
