package br.com.nexusbuilderlab.semanexus.core.catalogo;

/** Origem da demanda de fiscalização. */
public enum Origem implements Catalogavel {
    DENUNCIA("Denúncia"),
    MONITORAMENTO("Monitoramento"),
    SOLICITACAO_INSTITUCIONAL("Solicitação institucional"),
    ACAO_PLANEJADA("Ação planejada"),
    ANALISE_AMBIENTAL("Análise ambiental");

    private final String rotulo;

    Origem(String rotulo) {
        this.rotulo = rotulo;
    }

    @Override
    public String rotulo() {
        return rotulo;
    }
}
