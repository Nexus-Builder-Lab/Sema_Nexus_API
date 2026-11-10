package br.com.nexusbuilderlab.semanexus.core.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UsuarioTest {

    @Test
    void normalizaEmailParaMinusculasSemEspacos() {
        assertThat(Usuario.normalizarEmail("  Fiscal.Teste@Exemplo.COM ")).isEqualTo("fiscal.teste@exemplo.com");
        assertThat(Usuario.normalizarEmail(null)).isNull();
    }

    @Test
    void construtorESetterNormalizamEmailEMantemPadroes() {
        Usuario usuario = new Usuario("Pessoa Fictícia", "PESSOA@EXEMPLO.COM", "$2a$10$hash", Perfil.ANALISTA);
        assertThat(usuario.getEmail()).isEqualTo("pessoa@exemplo.com");
        assertThat(usuario.isAtivo()).isTrue();
        assertThat(usuario.isDemo()).isFalse();

        usuario.setEmail("Outra@Exemplo.com");
        assertThat(usuario.getEmail()).isEqualTo("outra@exemplo.com");
    }
}
