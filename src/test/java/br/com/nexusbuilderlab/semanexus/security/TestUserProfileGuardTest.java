package br.com.nexusbuilderlab.semanexus.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

/** Trava do usuário de teste: testes unitários, sem Oracle. */
class TestUserProfileGuardTest {

    private static final String EMAIL = "admin@admin.com";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);

    private TestUserProfileGuard guard(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new TestUserProfileGuard(usuarios, env);
    }

    private void executar(TestUserProfileGuard guard) {
        guard.run(new DefaultApplicationArguments());
    }

    @Test
    void profileProdComUsuarioDeTesteFalhaNaInicializacao() {
        when(usuarios.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> executar(guard("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(EMAIL)
                .hasMessageContaining("demo/dev");
    }

    @Test
    void profileProdSemUsuarioDeTesteSobeNormalmente() {
        when(usuarios.existsByEmail(EMAIL)).thenReturn(false);

        assertThatCode(() -> executar(guard("prod"))).doesNotThrowAnyException();
    }

    @Test
    void profileDemoComUsuarioDeTesteSobeNormalmente() {
        when(usuarios.existsByEmail(EMAIL)).thenReturn(true);

        assertThatCode(() -> executar(guard("demo"))).doesNotThrowAnyException();
        verifyNoInteractions(usuarios);
    }

    @Test
    void profileDevComUsuarioDeTesteSobeNormalmente() {
        when(usuarios.existsByEmail(EMAIL)).thenReturn(true);

        assertThatCode(() -> executar(guard("dev"))).doesNotThrowAnyException();
    }

    @Test
    void semProfileExplicitoUsaProfilePadraoDevENaoBloqueia() {
        MockEnvironment env = new MockEnvironment();
        env.setDefaultProfiles("dev");
        when(usuarios.existsByEmail(EMAIL)).thenReturn(true);

        assertThatCode(() -> executar(new TestUserProfileGuard(usuarios, env))).doesNotThrowAnyException();
    }

    @Test
    void profileDesconhecidoComUsuarioDeTesteFalha() {
        when(usuarios.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> executar(guard("staging"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mensagemDeErroNaoContemSenha() {
        when(usuarios.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> executar(guard("prod")))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("12345678"));
    }

    // ---- carga do bean por profile ----

    @Configuration
    static class Dependencias {
        @Bean
        UsuarioRepository usuarioRepository() {
            return mock(UsuarioRepository.class);
        }
    }

    private ApplicationContextRunner contexto(String... profiles) {
        return new ApplicationContextRunner()
                .withUserConfiguration(Dependencias.class, TestUserProfileGuard.class)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profiles));
    }

    @Test
    void guardaECarregadaEmTodosOsProfilesExcetoOpenapi() {
        for (String profile : new String[] {"prod", "demo", "dev", "staging"}) {
            contexto(profile).run(ctx -> assertThat(ctx).hasSingleBean(TestUserProfileGuard.class));
        }
        contexto().run(ctx -> assertThat(ctx).hasSingleBean(TestUserProfileGuard.class));
        contexto("prod", "openapi").run(ctx -> assertThat(ctx).doesNotHaveBean(TestUserProfileGuard.class));
    }
}
