package br.com.nexusbuilderlab.semanexus.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.setor.SetorRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** O seed só existe nos profiles demo/dev (e nunca com openapi, que roda sem banco). */
class DemoSeedProfileTest {

    @Configuration
    static class Dependencias {
        @Bean
        SetorRepository setorRepository() {
            return mock(SetorRepository.class);
        }

        @Bean
        UsuarioRepository usuarioRepository() {
            return mock(UsuarioRepository.class);
        }

        @Bean
        EquipeRepository equipeRepository() {
            return mock(EquipeRepository.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private ApplicationContextRunner contexto(String... profiles) {
        return new ApplicationContextRunner()
                .withUserConfiguration(Dependencias.class, DemoSeedService.class, DemoSeedRunner.class)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profiles));
    }

    @Test
    void profileProdNaoCarregaRunnerNemServico() {
        contexto("prod").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(DemoSeedRunner.class);
            assertThat(ctx).doesNotHaveBean(DemoSeedService.class);
        });
    }

    @Test
    void semProfileNaoCarregaRunner() {
        contexto().run(ctx -> assertThat(ctx).doesNotHaveBean(DemoSeedRunner.class));
    }

    @Test
    void profilesDemoEDevCarregamRunner() {
        contexto("demo").run(ctx -> assertThat(ctx).hasSingleBean(DemoSeedRunner.class));
        contexto("dev").run(ctx -> assertThat(ctx).hasSingleBean(DemoSeedRunner.class));
    }

    @Test
    void profileOpenapiNaoCarregaRunnerMesmoComDev() {
        contexto("dev", "openapi").run(ctx -> assertThat(ctx).doesNotHaveBean(DemoSeedRunner.class));
    }
}
