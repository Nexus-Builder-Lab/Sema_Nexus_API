package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import br.com.nexusbuilderlab.semanexus.common.dto.EquipeRef;
import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Registro de fiscalização da demanda (1:1 no MVP). */
@Schema(description = "Registro de fiscalização (simulado; não substitui sistemas oficiais nem gera autos de infração)")
public record FiscalizacaoDetalhe(
        @Schema(example = "4") Long id,
        @Schema(description = "Data da fiscalização", example = "2026-11-12") LocalDate data,
        EquipeRef equipe,
        @Schema(description = "Atividades realizadas") String atividades,
        @Schema(description = "Resultado da fiscalização") String resultado,
        @Schema(description = "Evidências registradas (texto)") String evidencias,
        @Schema(description = "Recomendações", nullable = true) String recomendacoes,
        UsuarioRef registradoPor,
        LocalDateTime registradoEm,
        @Schema(description = "Checklist estruturado (Issue #145); nulo em registros sem checklist", nullable = true)
        ChecklistPreenchido checklist) {

    /** Registro sem checklist. */
    public FiscalizacaoDetalhe(Long id, LocalDate data, EquipeRef equipe, String atividades, String resultado,
                               String evidencias, String recomendacoes, UsuarioRef registradoPor,
                               LocalDateTime registradoEm) {
        this(id, data, equipe, atividades, resultado, evidencias, recomendacoes, registradoPor, registradoEm, null);
    }
}
