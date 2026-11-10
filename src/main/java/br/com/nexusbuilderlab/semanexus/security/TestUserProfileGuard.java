package br.com.nexusbuilderlab.semanexus.security;

import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Trava de inicialização do usuário de teste (Issue #6): a credencial demonstrativa é fraca por definição,
 * então, fora dos profiles {@code demo}/{@code dev}, a API se recusa a iniciar se o usuário
 * {@value #EMAIL_USUARIO_TESTE} existir no banco.
 *
 * <p>Carregada em todos os profiles, exceto {@code openapi} — profile do CI que exporta o contrato sem
 * banco de dados (não há o que consultar). Executa antes de qualquer outro {@link ApplicationRunner}.
 * Somente leitura; não registra senha nem hash.
 */
@Component
@Profile("!openapi")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TestUserProfileGuard implements ApplicationRunner {

    /** E-mail (já normalizado) do usuário de teste criado pelo seed demonstrativo. */
    static final String EMAIL_USUARIO_TESTE = "admin@admin.com";

    private static final Logger log = LoggerFactory.getLogger(TestUserProfileGuard.class);

    private final UsuarioRepository usuarios;
    private final Environment environment;

    public TestUserProfileGuard(UsuarioRepository usuarios, Environment environment) {
        this.usuarios = usuarios;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        // acceptsProfiles considera também o profile padrão quando nenhum está ativo.
        if (environment.acceptsProfiles(Profiles.of("demo", "dev"))) {
            return;
        }
        if (usuarios.existsByEmail(EMAIL_USUARIO_TESTE)) {
            log.error("Inicialização recusada: o usuário de teste '{}' existe no banco, mas o profile ativo não é "
                    + "'demo' nem 'dev'. Remova esse usuário do banco ou inicie a API com o profile demo/dev "
                    + "(SPRING_PROFILES_ACTIVE).", EMAIL_USUARIO_TESTE);
            throw new IllegalStateException("Usuário de teste '" + EMAIL_USUARIO_TESTE
                    + "' presente fora dos profiles demo/dev: remova-o do banco ou use o profile demo/dev.");
        }
    }
}
