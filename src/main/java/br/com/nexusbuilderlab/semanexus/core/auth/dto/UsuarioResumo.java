package br.com.nexusbuilderlab.semanexus.core.auth.dto;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;

public record UsuarioResumo(Long id, String nome, String email, Perfil perfil) {

    public static UsuarioResumo de(Usuario usuario) {
        return new UsuarioResumo(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil());
    }
}
