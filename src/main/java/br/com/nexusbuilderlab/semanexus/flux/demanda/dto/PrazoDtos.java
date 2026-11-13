package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Respostas do controle de prazos (Issue #84). Dados fictícios: {@code demonstrativo} é sempre true. */
public final class PrazoDtos {

    private PrazoDtos() {
    }

    /** Estado do prazo de uma demanda com prazo definido. */
    public enum EstadoPrazo { VENCIDA, PROXIMA, NO_PRAZO, CONCLUIDA }

    @Schema(description = "Demanda vencida há mais tempo")
    public record MaisAntigaVencida(String numero, long diasAtraso) {
    }

    @Schema(description = "Contagem de demandas com prazo por estado")
    public record PrazoResumo(long vencidas, long proximas, long noPrazo, long concluidasNoPrazo,
                              long concluidasComAtraso, MaisAntigaVencida maisAntigaVencida, boolean demonstrativo) {
    }

    @Schema(description = "Linha do controle de prazos; diasAtraso para VENCIDA (e CONCLUIDA com atraso), "
            + "diasRestantes para PROXIMA/NO_PRAZO")
    public record PrazoItem(Long id, String numero, String titulo, String municipio, String equipe, String fiscal,
                            LocalDate prazo, String prioridade, String status, EstadoPrazo estado,
                            Long diasAtraso, Long diasRestantes, boolean demonstrativo) {
    }
}
