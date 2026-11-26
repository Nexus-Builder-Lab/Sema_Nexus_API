package br.com.nexusbuilderlab.semanexus.core.auditoria.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Registro de auditoria exposto pela API. {@code usuarioNome} é nulo para ações sem usuário (sistema/anônimo).
 * {@code valorAnterior}/{@code valorNovo} trazem só os campos alterados numa edição (Issue #148); {@code seqCadeia}
 * e {@code hashRegistro} são nulos em registros legados ou ainda não selados.
 */
public record AuditoriaDto(
        Long id,
        LocalDateTime data,
        Long usuarioId,
        String usuarioNome,
        String acao,
        String entidade,
        String entidadeId,
        String detalhe,
        @Schema(nullable = true, description = "Campos alterados → valor anterior (só em edições)")
        Map<String, String> valorAnterior,
        @Schema(nullable = true, description = "Campos alterados → valor novo (só em edições)")
        Map<String, String> valorNovo,
        @Schema(nullable = true, description = "Posição na cadeia SHA-256 (nula se legado ou pendente)")
        Long seqCadeia,
        @Schema(nullable = true, description = "Hash SHA-256 do registro (nulo se legado ou pendente)")
        String hashRegistro) {

    public AuditoriaDto(Long id, LocalDateTime data, Long usuarioId, String usuarioNome, String acao,
                        String entidade, String entidadeId, String detalhe) {
        this(id, data, usuarioId, usuarioNome, acao, entidade, entidadeId, detalhe, null, null, null, null);
    }
}
