package br.com.nexusbuilderlab.semanexus.core.catalogo;

/** Valor de enumeração publicado nos catálogos da API: código estável (nome do enum) e rótulo legível. */
public interface Catalogavel {

    /** Código estável usado em requisições, respostas e CHECKs do banco (nome do enum). */
    String name();

    /** Rótulo para exibição ao usuário. */
    String rotulo();
}
