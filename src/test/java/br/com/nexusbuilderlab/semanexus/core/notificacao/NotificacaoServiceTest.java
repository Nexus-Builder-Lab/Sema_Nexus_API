package br.com.nexusbuilderlab.semanexus.core.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class NotificacaoServiceTest {

    final Clock relogio = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC);
    final NotificacaoRepository repo = mock(NotificacaoRepository.class);
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final NotificacaoService service = new NotificacaoService(repo, usuarios, relogio);
    final UsuarioAutenticado ator = new UsuarioAutenticado(5L, "x", "x@x.test", Perfil.FISCAL);

    static Usuario usuario(long id, Perfil perfil) {
        Usuario u = new Usuario("U" + id, "u" + id + "@exemplo.test", "hash", perfil);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    void criaNotificacaoQuandoChaveNova() {
        when(repo.existsByUsuarioIdAndChave(7L, "K")).thenReturn(false);

        assertThat(service.notificar(7L, TipoNotificacao.DISTRIBUICAO, "t", "m", 1L, "K")).isTrue();

        verify(repo).save(any(Notificacao.class));
    }

    @Test
    void naoDuplicaQuandoChaveJaExiste() {
        when(repo.existsByUsuarioIdAndChave(7L, "PRAZO:1:2026-11-11")).thenReturn(true);

        assertThat(service.notificar(7L, TipoNotificacao.PRAZO_PROXIMO, "t", "m", 1L, "PRAZO:1:2026-11-11")).isFalse();

        verify(repo, never()).save(any());
    }

    @Test
    void notificarGestoresPulaOAutor() {
        when(usuarios.findByPerfilInAndAtivoTrue(any()))
                .thenReturn(List.of(usuario(1, Perfil.GESTOR), usuario(2, Perfil.ADMIN)));

        int criadas = service.notificarGestores(TipoNotificacao.NOVA_DEMANDA, "t", "m", 9L, "NOVA_DEMANDA:9", 1L);

        assertThat(criadas).isEqualTo(1);
        verify(repo).existsByUsuarioIdAndChave(2L, "NOVA_DEMANDA:9");
        verify(repo, never()).existsByUsuarioIdAndChave(1L, "NOVA_DEMANDA:9");
    }

    @Test
    void listaSomenteDoUsuarioLogado() {
        when(repo.findByUsuarioId(eq(5L), any())).thenReturn(new PageImpl<>(List.of()));

        service.listar(ator, null, PageRequest.of(0, 10));

        verify(repo).findByUsuarioId(eq(5L), any());
    }

    @Test
    void marcarLidaDeOutroUsuarioRetorna404() {
        when(repo.findByIdAndUsuarioId(50L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcarLida(ator, 50L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void marcarLidaAtualizaEContagemUsaOUsuario() {
        Notificacao n = new Notificacao(5L, TipoNotificacao.DISTRIBUICAO, "t", "m", 1L, "K", null);
        when(repo.findByIdAndUsuarioId(50L, 5L)).thenReturn(Optional.of(n));
        when(repo.save(n)).thenReturn(n);
        when(repo.countByUsuarioIdAndLidaFalse(5L)).thenReturn(3L);

        assertThat(service.marcarLida(ator, 50L).lida()).isTrue();
        assertThat(service.contarNaoLidas(ator).naoLidas()).isEqualTo(3L);
    }
}
