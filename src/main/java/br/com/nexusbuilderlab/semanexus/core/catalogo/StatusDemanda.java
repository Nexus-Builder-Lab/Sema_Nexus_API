package br.com.nexusbuilderlab.semanexus.core.catalogo;

/**
 * Os 7 status do fluxo da demanda, na ordem do fluxo (ver {@code docs/ARCHITECTURE.md} §5).
 * "Atrasada" <b>não</b> é status: é derivada ({@code prazo < hoje} e status ≠ FINALIZADA).
 */
public enum StatusDemanda implements Catalogavel {
    PENDENTE("Pendente"),
    EM_TRIAGEM("Em triagem"),
    AGUARDANDO_DISTRIBUICAO("Aguardando distribuição"),
    DISTRIBUIDA("Distribuída"),
    EM_ANDAMENTO("Em andamento"),
    AGUARDANDO_CONCLUSAO("Aguardando conclusão"),
    FINALIZADA("Finalizada");

    private final String rotulo;

    StatusDemanda(String rotulo) {
        this.rotulo = rotulo;
    }

    @Override
    public String rotulo() {
        return rotulo;
    }
}
