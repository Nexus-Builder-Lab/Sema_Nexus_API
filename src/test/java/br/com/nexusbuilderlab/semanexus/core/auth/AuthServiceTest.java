package br.com.nexusbuilderlab.semanexus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.exception.CredenciaisInvalidasException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.common.exception.UsuarioInativoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginRequest;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginResponse;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.RevogacaoTokenService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // Senha de teste fictícia (mínimo 8 caracteres), usada só aqui.
    static final String SENHA = "senha-de-teste-123";

    @Mock
    UsuarioRepository usuarios;
    @Mock
    AuditService auditoria;
    @Mock
    RevogacaoTokenService revogacao;

    PasswordEncoder encoder = spy(new BCryptPasswordEncoder(4));
    Clock relogio = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
    JwtService jwt = new JwtService("segredo-de-teste-com-mais-de-32-caracteres!!", Duration.ofHours(8), relogio);
    MockEnvironment ambiente = new MockEnvironment();
    AuthService servico;
    Usuario usuario;

    @BeforeEach
    void setUp() {
        servico = new AuthService(usuarios, encoder, jwt, auditoria, ambiente, relogio, revogacao);
        usuario = new Usuario("Fulano Fictício", "fulano@exemplo.test", encoder.encode(SENHA), Perfil.GESTOR);
        ReflectionTestUtils.setField(usuario, "id", 5L);
    }

    @Test
    void loginValidoRetornaTokenAtualizaUltimoAcessoEAudita() {
        when(usuarios.findByEmail("fulano@exemplo.test")).thenReturn(Optional.of(usuario));

        LoginResponse resposta = servico.login(new LoginRequest("  Fulano@Exemplo.TEST ", SENHA));

        assertThat(resposta.expiresIn()).isEqualTo(28_800L);
        assertThat(resposta.usuario().email()).isEqualTo("fulano@exemplo.test");
        assertThat(resposta.usuario().perfil()).isEqualTo(Perfil.GESTOR);
        assertThat(jwt.validar(resposta.token())).isPresent();
        assertThat(usuario.getUltimoAcesso()).isNotNull();
        verify(auditoria).registrarComo(eq(5L), eq("LOGIN"), eq("USUARIO"), eq("5"), any());
    }

    @Test
    void senhaIncorretaRetornaMensagemGenericaESemAuditoriaDeLogin() {
        when(usuarios.findByEmail("fulano@exemplo.test")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> servico.login(new LoginRequest("fulano@exemplo.test", "errada-123456")))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(auditoria, never()).registrarComo(any(), any(), any(), any(), any());
        assertThat(usuario.getUltimoAcesso()).isNull();
    }

    @Test
    void emailInexistenteTemMesmaMensagemEAindaExecutaBcrypt() {
        when(usuarios.findByEmail("naoexiste@exemplo.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.login(new LoginRequest("naoexiste@exemplo.test", SENHA)))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        // matches() contra o hash descartável: mesmo custo de uma senha errada de usuário existente.
        verify(encoder).matches(eq(SENHA), any());
    }

    @Test
    void usuarioInativoComSenhaCorretaRecebe403() {
        usuario.setAtivo(false);
        when(usuarios.findByEmail("fulano@exemplo.test")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> servico.login(new LoginRequest("fulano@exemplo.test", SENHA)))
                .isInstanceOf(UsuarioInativoException.class);
        verify(auditoria, never()).registrarComo(any(), any(), any(), any(), any());
    }

    @Test
    void usuarioInativoComSenhaErradaNaoRevelaSituacao() {
        usuario.setAtivo(false);
        when(usuarios.findByEmail("fulano@exemplo.test")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> servico.login(new LoginRequest("fulano@exemplo.test", "errada-123456")))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void logoutRevogaOTokenERegistraNaAuditoria() {
        Instant expira = Instant.parse("2026-11-10T20:00:00Z");
        when(revogacao.revogar("jti-de-teste", 5L, expira)).thenReturn(true);
        servico.logout(5L, "jti-de-teste", expira);

        verify(revogacao).revogar("jti-de-teste", 5L, expira);
        verify(auditoria).registrarComo(eq(5L), eq("LOGOUT"), eq("USUARIO"), eq("5"), any());
    }

    @Test
    void logoutDeTokenJaRevogadoNaoDuplicaAuditoria() {
        Instant expira = Instant.parse("2026-11-10T20:00:00Z");
        when(revogacao.revogar("jti-de-teste", 5L, expira)).thenReturn(false);

        servico.logout(5L, "jti-de-teste", expira);

        verifyNoInteractions(auditoria);
    }

    @Test
    void meRetornaResumoOuNaoEncontrado() {
        when(usuarios.findById(5L)).thenReturn(Optional.of(usuario));
        when(usuarios.findById(99L)).thenReturn(Optional.empty());

        assertThat(servico.me(5L).nome()).isEqualTo("Fulano Fictício");
        assertThatThrownBy(() -> servico.me(99L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void configuracaoDemoDependeDoProfileDemo() {
        assertThat(servico.configuracao().demo()).isFalse();

        ambiente.setActiveProfiles("demo");

        assertThat(servico.configuracao().demo()).isTrue();
    }

    @Test
    void configuracaoDemoListaPerfisSomenteNoProfileDemo() {
        assertThat(servico.configuracao().perfisDemo()).isNull();
        ambiente.setActiveProfiles("demo");
        var perfis = servico.configuracao().perfisDemo();
        assertThat(perfis).extracting(p -> p.perfil()).containsExactly(Perfil.values());
        assertThat(perfis).allSatisfy(p -> assertThat(p.nome()).isNotBlank());
    }

    @Test
    void loginDemoDevolveTokenDoUsuarioDemoEAuditaModoDemonstracao() {
        when(usuarios.findFirstByPerfilAndDemoTrueAndAtivoTrueOrderByIdAsc(Perfil.GESTOR))
                .thenReturn(Optional.of(usuario));

        LoginResponse r = servico.loginDemo("gestor");

        assertThat(r.usuario().id()).isEqualTo(5L);
        assertThat(jwt.validar(r.token())).isPresent();
        verify(auditoria).registrarComo(eq(5L), eq("LOGIN"), eq("USUARIO"), eq("5"),
                org.mockito.ArgumentMatchers.contains("modo demonstração"));
        verify(encoder, never()).matches(any(), any());
    }

    @Test
    void loginDemoFiscalUsaOFiscalDoNucleoCuiaba() {
        Usuario cuiaba = new Usuario("Fiscal Cuiabá", "fiscal.cba1@demo.semaalerta.local", "hash", Perfil.FISCAL);
        ReflectionTestUtils.setField(cuiaba, "id", 31L);
        cuiaba.setDemo(true);
        when(usuarios.findByEmail("fiscal.cba1@demo.semaalerta.local")).thenReturn(Optional.of(cuiaba));

        assertThat(servico.loginDemo("FISCAL").usuario().id()).isEqualTo(31L);
        verify(usuarios, never()).findFirstByPerfilAndDemoTrueAndAtivoTrueOrderByIdAsc(Perfil.FISCAL);

        // conta preferida inativa: volta para o primeiro fiscal demonstrativo
        cuiaba.setAtivo(false);
        Usuario primeiro = new Usuario("Fiscal Um", "fiscal1@demo.semaalerta.local", "hash", Perfil.FISCAL);
        ReflectionTestUtils.setField(primeiro, "id", 3L);
        when(usuarios.findFirstByPerfilAndDemoTrueAndAtivoTrueOrderByIdAsc(Perfil.FISCAL))
                .thenReturn(Optional.of(primeiro));
        assertThat(servico.loginDemo("FISCAL").usuario().id()).isEqualTo(3L);
    }

    @Test
    void loginDemoPerfilInvalidoOuSemUsuarioFalha() {
        assertThatThrownBy(() -> servico.loginDemo("XPTO"))
                .isInstanceOf(br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException.class);
        when(usuarios.findFirstByPerfilAndDemoTrueAndAtivoTrueOrderByIdAsc(Perfil.FISCAL))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> servico.loginDemo("FISCAL")).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(auditoria, never()).registrarComo(any(), eq("LOGIN"), any(), any(), any());
    }

    @Test
    void dtosNaoExpoemSenhaNemTokenEmToString() {
        assertThat(new LoginRequest("a@b.test", SENHA).toString()).doesNotContain(SENHA);
        assertThat(new LoginResponse("tok.en.secreto", 1, null).toString()).doesNotContain("secreto");
    }
}
