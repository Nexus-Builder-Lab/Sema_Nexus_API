package br.com.nexusbuilderlab.semanexus.core.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.setor.SetorRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.dto.UsuarioAtualizacaoRequest;
import br.com.nexusbuilderlab.semanexus.core.usuario.dto.UsuarioCriacaoRequest;
import br.com.nexusbuilderlab.semanexus.core.usuario.dto.UsuarioDto;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    UsuarioRepository usuarios;
    @Mock
    SetorRepository setores;
    @Mock
    AuditService auditoria;
    final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    UsuarioService service() {
        return new UsuarioService(usuarios, setores, encoder, auditoria);
    }

    private static Usuario usuario(long id, Perfil perfil, boolean ativo) {
        Usuario u = new Usuario("Fulano " + id, "u" + id + "@exemplo.test", "hash", perfil);
        ReflectionTestUtils.setField(u, "id", id);
        u.setAtivo(ativo);
        return u;
    }

    @Test
    void criaUsuarioComHashBcryptEAuditaSemSenha() {
        when(usuarios.existsByEmail("novo@exemplo.test")).thenReturn(false);
        when(setores.findById(1L)).thenReturn(Optional.of(new Setor("S", "Setor")));
        when(usuarios.save(any())).thenAnswer(i -> i.getArgument(0));

        UsuarioDto dto = service().criar(new UsuarioCriacaoRequest(" Novo ", "Novo@Exemplo.test", "senhaSegura1",
                Perfil.FISCAL, 1L));

        ArgumentCaptor<Usuario> c = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(c.capture());
        assertThat(c.getValue().getSenhaHash()).isNotEqualTo("senhaSegura1");
        assertThat(encoder.matches("senhaSegura1", c.getValue().getSenhaHash())).isTrue();
        assertThat(dto.email()).isEqualTo("novo@exemplo.test");
        assertThat(dto.nome()).isEqualTo("Novo");
        ArgumentCaptor<String> detalhe = ArgumentCaptor.forClass(String.class);
        verify(auditoria).registrar(eq("CRIAR"), eq("USUARIO"), any(), detalhe.capture());
        assertThat(detalhe.getValue()).doesNotContain("senhaSegura1");
    }

    @Test
    void senhaCurtaNaCriacaoRecusada() {
        assertThatThrownBy(() -> service().criar(new UsuarioCriacaoRequest("N", "n@exemplo.test", "1234567",
                Perfil.FISCAL, null))).isInstanceOf(DadosInvalidosException.class);
        verify(usuarios, never()).save(any());
    }

    @Test
    void senhaDeOitoCaracteresAceita() {
        when(usuarios.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service().criar(new UsuarioCriacaoRequest("N", "n@exemplo.test", "12345678", Perfil.CONSULTA, null)))
                .isNotNull();
    }

    @Test
    void emailDuplicadoNaCriacaoRetornaConflito() {
        when(usuarios.existsByEmail("n@exemplo.test")).thenReturn(true);
        assertThatThrownBy(() -> service().criar(new UsuarioCriacaoRequest("N", "N@exemplo.test", "12345678",
                Perfil.FISCAL, null))).isInstanceOf(ConflitoException.class);
    }

    @Test
    void setorInexistenteRetorna400() {
        when(setores.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().criar(new UsuarioCriacaoRequest("N", "n@exemplo.test", "12345678",
                Perfil.FISCAL, 9L))).isInstanceOf(DadosInvalidosException.class);
    }

    @Test
    void desativarUltimoAdminAtivoRetornaConflito() {
        Usuario admin = usuario(1, Perfil.ADMIN, true);
        when(usuarios.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarios.countByPerfilAndAtivoAndIdNot(Perfil.ADMIN, true, 1L)).thenReturn(0L);

        assertThatThrownBy(() -> service().alterarAtivacao(1L, false)).isInstanceOf(ConflitoException.class);
        assertThat(admin.isAtivo()).isTrue();
        verify(auditoria, never()).registrar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void desativarAdminComOutroAtivoPermitidoEAudita() {
        Usuario admin = usuario(1, Perfil.ADMIN, true);
        when(usuarios.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarios.countByPerfilAndAtivoAndIdNot(Perfil.ADMIN, true, 1L)).thenReturn(1L);

        assertThat(service().alterarAtivacao(1L, false).ativo()).isFalse();
        verify(auditoria).registrarAlteracao(eq("DESATIVAR"), eq("USUARIO"), eq("1"), anyString(),
                org.mockito.ArgumentMatchers.argThat(a -> "true".equals(a.antes().get("ativo"))
                        && "false".equals(a.depois().get("ativo"))));
    }

    @Test
    void desativarNaoAdminNaoConsultaContagemDeAdmins() {
        Usuario fiscal = usuario(2, Perfil.FISCAL, true);
        when(usuarios.findById(2L)).thenReturn(Optional.of(fiscal));

        service().alterarAtivacao(2L, false);

        verify(usuarios, never()).countByPerfilAndAtivoAndIdNot(any(), eq(true), any());
    }

    @Test
    void rebaixarUltimoAdminRetornaConflito() {
        Usuario admin = usuario(1, Perfil.ADMIN, true);
        when(usuarios.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarios.findByEmail("u1@exemplo.test")).thenReturn(Optional.of(admin));
        when(usuarios.countByPerfilAndAtivoAndIdNot(Perfil.ADMIN, true, 1L)).thenReturn(0L);

        assertThatThrownBy(() -> service().atualizar(1L,
                new UsuarioAtualizacaoRequest("Fulano", "u1@exemplo.test", null, Perfil.GESTOR, null)))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void atualizarSemSenhaMantemHashEComSenhaCurtaRecusa() {
        Usuario u = usuario(2, Perfil.FISCAL, true);
        when(usuarios.findById(2L)).thenReturn(Optional.of(u));
        when(usuarios.findByEmail("u2@exemplo.test")).thenReturn(Optional.of(u));

        service().atualizar(2L, new UsuarioAtualizacaoRequest("Novo Nome", "u2@exemplo.test", null, Perfil.ANALISTA, null));
        assertThat(u.getSenhaHash()).isEqualTo("hash");
        assertThat(u.getPerfil()).isEqualTo(Perfil.ANALISTA);

        assertThatThrownBy(() -> service().atualizar(2L,
                new UsuarioAtualizacaoRequest("Novo Nome", "u2@exemplo.test", "curta", Perfil.ANALISTA, null)))
                .isInstanceOf(DadosInvalidosException.class);
        assertThat(u.getSenhaHash()).isEqualTo("hash");
    }

    @Test
    void atualizarComSenhaRedefineHashEAuditaSemExpor() {
        Usuario u = usuario(2, Perfil.FISCAL, true);
        when(usuarios.findById(2L)).thenReturn(Optional.of(u));
        when(usuarios.findByEmail("u2@exemplo.test")).thenReturn(Optional.empty());

        service().atualizar(2L, new UsuarioAtualizacaoRequest("N", "u2@exemplo.test", "novaSenha99", Perfil.FISCAL, null));

        assertThat(encoder.matches("novaSenha99", u.getSenhaHash())).isTrue();
        var alteracoes = org.mockito.ArgumentCaptor.forClass(
                br.com.nexusbuilderlab.semanexus.core.auditoria.Alteracoes.class);
        verify(auditoria).registrarAlteracao(eq("ATUALIZAR"), eq("USUARIO"), eq("2"), contains("senha redefinida"),
                alteracoes.capture());
        // A senha nunca aparece no antes/depois: só a indicação de redefinição.
        assertThat(alteracoes.getValue().depois()).containsEntry("senha", "(redefinida)").containsEntry("nome", "N");
        assertThat(alteracoes.getValue().depois().values()).doesNotContain("novaSenha99");
    }

    @Test
    void atualizarComSenhaPreservaMarcadoresClarosNoAuditServiceReal() {
        var auditoriaRepo = org.mockito.Mockito.mock(
                br.com.nexusbuilderlab.semanexus.core.auditoria.AuditoriaRepository.class);
        var selador = org.mockito.Mockito.mock(
                br.com.nexusbuilderlab.semanexus.core.auditoria.SeladorAuditoria.class);
        var auditReal = new AuditService(auditoriaRepo, usuarios, selador);
        var servicoReal = new UsuarioService(usuarios, setores, encoder, auditReal);
        Usuario u = usuario(2, Perfil.FISCAL, true);
        when(usuarios.findById(2L)).thenReturn(Optional.of(u));
        when(usuarios.findByEmail("u2@exemplo.test")).thenReturn(Optional.empty());

        servicoReal.atualizar(2L, new UsuarioAtualizacaoRequest("N", "u2@exemplo.test", "novaSenha99",
                Perfil.FISCAL, null));

        var salvo = ArgumentCaptor.forClass(br.com.nexusbuilderlab.semanexus.core.auditoria.Auditoria.class);
        verify(auditoriaRepo).save(salvo.capture());
        assertThat(salvo.getValue().getValorAnterior()).contains("\"senha\":\"(mantida)\"");
        assertThat(salvo.getValue().getValorNovo()).contains("\"senha\":\"(redefinida)\"");
        assertThat(salvo.getValue().getValorNovo()).doesNotContain("novaSenha99", "***");
    }

    @Test
    void emailDeOutroUsuarioNaEdicaoRetornaConflito() {
        Usuario u = usuario(2, Perfil.FISCAL, true);
        when(usuarios.findById(2L)).thenReturn(Optional.of(u));
        when(usuarios.findByEmail("u3@exemplo.test")).thenReturn(Optional.of(usuario(3, Perfil.FISCAL, true)));

        assertThatThrownBy(() -> service().atualizar(2L,
                new UsuarioAtualizacaoRequest("N", "u3@exemplo.test", null, Perfil.FISCAL, null)))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void usuarioInexistenteRetorna404() {
        when(usuarios.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().buscar(7L)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service().alterarAtivacao(7L, true)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
