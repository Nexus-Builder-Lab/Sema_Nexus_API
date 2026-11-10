package br.com.nexusbuilderlab.semanexus.common.dto;

import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

/** Referência resumida a um usuário (sem e-mail nem dados sensíveis). */
@Schema(description = "Referência resumida a um usuário")
public record UsuarioRef(
        @Schema(example = "3") Long id,
        @Schema(example = "Fiscal Demonstrativo 1") String nome) {

    public static UsuarioRef de(Usuario u) {
        return u == null ? null : new UsuarioRef(u.getId(), u.getNome());
    }
}
