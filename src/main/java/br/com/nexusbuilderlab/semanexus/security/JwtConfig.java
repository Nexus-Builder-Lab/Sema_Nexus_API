package br.com.nexusbuilderlab.semanexus.security;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

/**
 * Cria o {@link JwtService}. {@code JWT_SECRET} é obrigatório (mínimo 32 bytes) fora dos profiles
 * {@code dev} e {@code openapi}; nesses dois, se ausente, usa-se um segredo aleatório efêmero
 * (os tokens deixam de valer a cada reinício — aceitável em desenvolvimento e na exportação do contrato).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    JwtService jwtService(JwtProperties props, Environment env, Clock clock) {
        String segredo = props.secret();
        if (!StringUtils.hasText(segredo)) {
            if (!env.acceptsProfiles(Profiles.of("dev", "openapi"))) {
                throw new IllegalStateException(
                        "JWT_SECRET não definido. Informe a variável de ambiente JWT_SECRET (mínimo 32 caracteres).");
            }
            log.warn("JWT_SECRET não definido: usando segredo efêmero (permitido apenas nos profiles dev/openapi).");
            byte[] aleatorio = new byte[48];
            new SecureRandom().nextBytes(aleatorio);
            segredo = Base64.getEncoder().encodeToString(aleatorio);
        }
        return new JwtService(segredo, props.expiration(), clock);
    }
}
