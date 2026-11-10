package br.com.nexusbuilderlab.semanexus.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "admin@admin.com") @NotBlank(message = "informe o e-mail") @Size(max = 254, message = "e-mail muito longo") String email,
        @Schema(description = "Senha do usuário (nunca é registrada em log)", example = "********") @NotBlank(message = "informe a senha") @Size(max = 128, message = "senha muito longa") String senha) {

    /** Evita vazar a senha em {@code toString()} acidental (logs). */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", senha=***]";
    }
}
