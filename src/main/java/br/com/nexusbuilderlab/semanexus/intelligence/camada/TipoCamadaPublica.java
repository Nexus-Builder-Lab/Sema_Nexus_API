package br.com.nexusbuilderlab.semanexus.intelligence.camada;

/** Tipos de camadas públicas abertas offline de Mato Grosso (Issue #144). */
public enum TipoCamadaPublica {
    UNIDADE_CONSERVACAO("Unidades de Conservação (MMA/CNUC)"),
    TERRA_INDIGENA("Terras Indígenas (FUNAI)"),
    HIDROGRAFIA("Hidrografia principal (ANA)");

    private final String rotulo;

    TipoCamadaPublica(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
