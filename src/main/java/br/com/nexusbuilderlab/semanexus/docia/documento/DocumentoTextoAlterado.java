package br.com.nexusbuilderlab.semanexus.docia.documento;

/**
 * Evento publicado quando um documento é criado ou tem o texto alterado (Issue #141). Depois do commit, a indexação
 * vetorial ({@code IndexadorTrechos}) recalcula os trechos em segundo plano, sem atrasar a resposta do upload.
 */
public record DocumentoTextoAlterado(long documentoId) {
}
