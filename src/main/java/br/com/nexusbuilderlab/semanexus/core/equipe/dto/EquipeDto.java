package br.com.nexusbuilderlab.semanexus.core.equipe.dto;

import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import io.swagger.v3.oas.annotations.media.Schema;

/** Equipe de fiscalização/análise com setor, responsável e total de membros. */
@Schema(description = "Equipe de fiscalização/análise (fictícia na demonstração)")
public record EquipeDto(
        @Schema(example = "1") Long id,
        @Schema(example = "Equipe Alfa (demonstração)") String nome,
        @Schema(example = "2") Long setorId,
        @Schema(example = "DEMO-FISC") String setorSigla,
        @Schema(description = "Responsável da equipe; ausente quando não definido") UsuarioRef responsavel,
        @Schema(description = "Quantidade de membros", example = "3") long totalMembros,
        @Schema(example = "true") boolean ativo) {

    public static EquipeDto de(Equipe e, long totalMembros) {
        return new EquipeDto(e.getId(), e.getNome(), e.getSetor().getId(), e.getSetor().getSigla(),
                UsuarioRef.de(e.getResponsavel()), totalMembros, e.isAtivo());
    }
}
