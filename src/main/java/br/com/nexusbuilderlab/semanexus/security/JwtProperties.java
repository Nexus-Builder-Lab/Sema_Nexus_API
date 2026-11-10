package br.com.nexusbuilderlab.semanexus.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do JWT ({@code semanexus.jwt.*}). O segredo vem somente da variável de ambiente
 * {@code JWT_SECRET} (mínimo 32 bytes); nunca é gravado em arquivo versionado.
 */
@ConfigurationProperties(prefix = "semanexus.jwt")
public record JwtProperties(String secret, @DefaultValue("8h") Duration expiration) {
}
