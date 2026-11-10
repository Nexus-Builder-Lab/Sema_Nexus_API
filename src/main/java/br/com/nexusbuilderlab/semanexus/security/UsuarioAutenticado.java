package br.com.nexusbuilderlab.semanexus.security;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;

/** Principal do usuário autenticado pelo token JWT (disponível via {@code @AuthenticationPrincipal}). */
public record UsuarioAutenticado(Long id, String nome, String email, Perfil perfil) {

    public static UsuarioAutenticado de(Usuario usuario) {
        return new UsuarioAutenticado(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil());
    }
}
