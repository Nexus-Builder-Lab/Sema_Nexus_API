package br.com.nexusbuilderlab.semanexus.core.usuario.dto;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** Usuário para a gestão administrativa (nunca expõe senha ou hash). */
@Schema(description = "Usuário da plataforma (fictício na demonstração)")
public record UsuarioDto(
        @Schema(example = "3") Long id,
        @Schema(example = "Fiscal Demonstrativo 1") String nome,
        @Schema(example = "fiscal1@demo.semaalerta.local") String email,
        @Schema(example = "FISCAL") Perfil perfil,
        @Schema(example = "1") Long setorId,
        @Schema(example = "DEMO-FISC") String setorSigla,
        @Schema(example = "true") boolean ativo,
        LocalDateTime ultimoAcesso,
        LocalDateTime criadoEm) {

    public static UsuarioDto de(Usuario u) {
        return new UsuarioDto(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(),
                u.getSetor() == null ? null : u.getSetor().getId(),
                u.getSetor() == null ? null : u.getSetor().getSigla(),
                u.isAtivo(), u.getUltimoAcesso(), u.getCriadoEm());
    }
}
