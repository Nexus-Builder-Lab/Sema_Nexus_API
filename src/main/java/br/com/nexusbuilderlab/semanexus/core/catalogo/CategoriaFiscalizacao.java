package br.com.nexusbuilderlab.semanexus.core.catalogo;

/** Categoria ambiental da demanda (somente fiscalização). */
public enum CategoriaFiscalizacao implements Catalogavel {
    DESMATAMENTO_ILEGAL("Desmatamento ilegal"),
    QUEIMADA_INCENDIO("Queimada/incêndio"),
    RECURSOS_HIDRICOS("Recursos hídricos"),
    FAUNA_SILVESTRE("Fauna silvestre"),
    PESCA_IRREGULAR("Pesca irregular"),
    MINERACAO_IRREGULAR("Mineração irregular"),
    RESIDUOS_POLUICAO("Resíduos e poluição"),
    AREA_PRESERVACAO_PERMANENTE("Área de preservação permanente");

    private final String rotulo;

    CategoriaFiscalizacao(String rotulo) {
        this.rotulo = rotulo;
    }

    @Override
    public String rotulo() {
        return rotulo;
    }
}
