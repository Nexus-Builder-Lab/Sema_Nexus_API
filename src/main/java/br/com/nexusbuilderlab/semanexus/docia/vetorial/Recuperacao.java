package br.com.nexusbuilderlab.semanexus.docia.vetorial;

/**
 * Como os documentos foram recuperados (Issue #141): {@code LEXICAL} = só a busca textual (termos/radicais);
 * {@code HIBRIDA} = busca textual + busca vetorial (embeddings locais) fundidas por RRF. A Web mostra o modo para o
 * usuário saber o que sustentou o resultado.
 */
public enum Recuperacao {
    LEXICAL,
    HIBRIDA
}
