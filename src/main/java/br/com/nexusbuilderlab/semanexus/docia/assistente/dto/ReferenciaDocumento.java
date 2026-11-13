package br.com.nexusbuilderlab.semanexus.docia.assistente.dto;

/** Citação: documento de origem e trecho usado na resposta. */
public record ReferenciaDocumento(Long documentoId, String titulo, String trecho) {
}
