package br.com.nexusbuilderlab.semanexus.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();
    private final Clock relogio = Clock.systemUTC();

    private static MockEnvironment ambiente(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return env;
    }

    @Test
    void foraDeDevSemSegredoNaoSobe() {
        for (String profile : new String[] {"demo", "prod"}) {
            assertThatThrownBy(() -> config.jwtService(
                    new JwtProperties("", Duration.ofHours(8)), ambiente(profile), relogio))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET");
        }
    }

    @Test
    void emDevSemSegredoUsaSegredoEfemero() {
        JwtService servico = config.jwtService(new JwtProperties(null, Duration.ofHours(8)), ambiente("dev"), relogio);

        assertThat(servico.expiresInSegundos()).isEqualTo(28_800L);
    }

    @Test
    void segredoCurtoNaoSobeNemEmDev() {
        assertThatThrownBy(() -> config.jwtService(
                new JwtProperties("curto", Duration.ofHours(8)), ambiente("dev"), relogio))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void segredoValidoEAceitoEmQualquerProfile() {
        JwtService servico = config.jwtService(
                new JwtProperties("x".repeat(40), Duration.ofHours(8)), ambiente("prod"), relogio);

        assertThat(servico).isNotNull();
    }
}
