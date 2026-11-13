package br.com.nexusbuilderlab.semanexus.core.usuario.dto;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Edição de usuário. {@code senha} é opcional: se informada, redefine a senha (mínimo 8 caracteres). */
@Schema(description = "Edição de usuário")
public record UsuarioAtualizacaoRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 254) String email,
        @Schema(description = "Opcional; se informada redefine a senha (8 a 72 caracteres)",
                accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String senha,
        @NotNull Perfil perfil,
        @Schema(description = "Setor do usuário; ausente remove o setor") Long setorId) {
}
