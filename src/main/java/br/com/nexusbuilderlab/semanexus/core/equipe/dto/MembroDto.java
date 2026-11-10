package br.com.nexusbuilderlab.semanexus.core.equipe.dto;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

/** Membro de uma equipe (nunca expõe senha ou hash). */
@Schema(description = "Membro de uma equipe")
public record MembroDto(
        @Schema(example = "3") Long id,
        @Schema(example = "Fiscal Demonstrativo 1") String nome,
        @Schema(example = "fiscal1@demo.semanexus.local") String email,
        @Schema(example = "FISCAL") Perfil perfil,
        @Schema(example = "true") boolean ativo) {

    public static MembroDto de(Usuario u) {
        return new MembroDto(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), u.isAtivo());
    }
}
