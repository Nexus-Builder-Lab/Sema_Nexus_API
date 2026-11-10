package br.com.nexusbuilderlab.semanexus.core.municipio;

import br.com.nexusbuilderlab.semanexus.core.catalogo.Catalogavel;

/** Biomas de Mato Grosso (valores do CHECK {@code CK_MUNICIPIO_BIOMA}). */
public enum Bioma implements Catalogavel {
    AMAZONIA("Amazônia"),
    CERRADO("Cerrado"),
    PANTANAL("Pantanal");

    private final String rotulo;

    Bioma(String rotulo) {
        this.rotulo = rotulo;
    }

    @Override
    public String rotulo() {
        return rotulo;
    }
}
