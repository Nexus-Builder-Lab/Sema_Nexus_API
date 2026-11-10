package br.com.nexusbuilderlab.semanexus.core.setor.dto;

import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import io.swagger.v3.oas.annotations.media.Schema;

/** Setor organizacional (fictício na demonstração). */
@Schema(description = "Setor organizacional (fictício na demonstração)")
public record SetorDto(
        @Schema(example = "1") Long id,
        @Schema(example = "DEMO-FISC") String sigla,
        @Schema(example = "Setor de Fiscalização (demonstração)") String nome,
        @Schema(example = "true") boolean ativo) {

    public static SetorDto de(Setor s) {
        return new SetorDto(s.getId(), s.getSigla(), s.getNome(), s.isAtivo());
    }
}
