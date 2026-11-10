package br.com.nexusbuilderlab.semanexus.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados do contrato OpenAPI (exportado pelo CI como artefato {@code openapi.json}). */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI semaNexusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SEMA NEXUS API")
                        .version("0.1.0")
                        .description("""
                                AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.

                                API da plataforma demonstrativa SEMA NEXUS (inteligência ambiental, \
                                demandas de fiscalização e gestão documental em Mato Grosso). \
                                Sem integração com sistemas institucionais.

                                Padrões: prefixo /api; paginação ?page&size&sort; erros em \
                                application/problem+json (RFC 9457)."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
