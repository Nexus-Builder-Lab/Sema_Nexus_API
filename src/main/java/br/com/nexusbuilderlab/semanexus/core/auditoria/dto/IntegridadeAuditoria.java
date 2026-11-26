package br.com.nexusbuilderlab.semanexus.core.auditoria.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** Resultado da conferência da trilha de auditoria encadeada (Issue #148). */
@Schema(description = "Conferência da cadeia SHA-256 da auditoria")
public record IntegridadeAuditoria(
        @Schema(description = "true quando todos os registros selados conferem e o fim da cadeia bate com o estado")
        boolean integra,
        @Schema(description = "INTEGRA ou VIOLADA")
        String situacao,
        @Schema(description = "Algoritmo do encadeamento", example = "SHA-256")
        String algoritmo,
        @Schema(description = "Registros selados conferidos")
        long registrosVerificados,
        @Schema(description = "Registros anteriores à cadeia (sem hash; integridade não garantida)")
        long registrosLegados,
        @Schema(description = "Registros novos aguardando selagem (normalmente selados em milissegundos)")
        long registrosPendentes,
        @Schema(description = "Última posição da cadeia registrada")
        long ultimaSequencia,
        @Schema(nullable = true, description = "Hash do último registro selado")
        String ultimoHash,
        @Schema(nullable = true, description = "Primeira falha encontrada (nula quando íntegra)")
        FalhaIntegridade primeiraFalha,
        LocalDateTime verificadaEm) {

    @Schema(description = "Primeira falha da cadeia")
    public record FalhaIntegridade(
            @Schema(nullable = true) Long sequencia,
            @Schema(nullable = true) Long auditoriaId,
            @Schema(description = "HASH_DIVERGENTE, ENCADEAMENTO_QUEBRADO, SEQUENCIA_AUSENTE ou FIM_DIVERGENTE")
            String motivo,
            String descricao) {
    }
}
