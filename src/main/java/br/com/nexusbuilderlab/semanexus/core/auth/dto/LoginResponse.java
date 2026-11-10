package br.com.nexusbuilderlab.semanexus.core.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        String token,
        @Schema(description = "Validade do token em segundos", example = "28800") long expiresIn,
        UsuarioResumo usuario) {

    /** Evita vazar o token em {@code toString()} acidental (logs). */
    @Override
    public String toString() {
        return "LoginResponse[token=***, expiresIn=" + expiresIn + ", usuario=" + usuario + "]";
    }
}
