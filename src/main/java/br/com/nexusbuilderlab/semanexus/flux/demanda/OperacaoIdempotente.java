package br.com.nexusbuilderlab.semanexus.flux.demanda;

/** Operações da execução em campo protegidas por chave de idempotência na sincronização offline (Issue #147). */
public enum OperacaoIdempotente {
    INICIO,
    ATIVIDADE,
    FISCALIZACAO
}
