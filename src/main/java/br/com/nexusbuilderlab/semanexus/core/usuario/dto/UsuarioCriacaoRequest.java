package br.com.nexusbuilderlab.semanexus.core.usuario.dto;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cadastro de usuário. A senha exige no mínimo 8 caracteres (política única, sem exceções). */
@Schema(description = "Cadastro de usuário")
public record UsuarioCriacaoRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 254) String email,
        @Schema(description = "Mínimo de 8 caracteres (máximo 72)", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String senha,
        @NotNull Perfil perfil,
        @Schema(description = "Setor do usuário; opcional") Long setorId) {
}
