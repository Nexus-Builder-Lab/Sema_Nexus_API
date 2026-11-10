package br.com.nexusbuilderlab.semanexus.core.catalogo;

/** Prioridade da demanda, da menor para a maior. */
public enum Prioridade implements Catalogavel {
    BAIXA("Baixa"),
    MEDIA("Média"),
    ALTA("Alta"),
    URGENTE("Urgente");

    private final String rotulo;

    Prioridade(String rotulo) {
        this.rotulo = rotulo;
    }

    @Override
    public String rotulo() {
        return rotulo;
    }
}
