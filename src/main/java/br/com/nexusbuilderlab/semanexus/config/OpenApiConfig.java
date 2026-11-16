package br.com.nexusbuilderlab.semanexus.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
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
                        .title("SEMA ALERTA API")
                        .version("0.1.0")
                        .description("""
                                AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.

                                API da plataforma demonstrativa SEMA ALERTA (inteligência ambiental, \
                                demandas de fiscalização e gestão documental em Mato Grosso). \
                                Sem integração com sistemas institucionais.

                                Padrões: prefixo /api; paginação ?page&size&sort; erros em \
                                application/problem+json (RFC 9457)."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSchemas("ErroCampo", erroCampo())
                        .addSchemas("Problema", problema()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** Erro de validação de um campo (preenchido em {@code Problema.errors}). */
    private static Schema<?> erroCampo() {
        return new ObjectSchema()
                .description("Erro de validação de um campo")
                .addProperty("campo", new StringSchema().example("titulo"))
                .addProperty("mensagem", new StringSchema().example("tamanho deve ser entre 5 e 200"));
    }

    /** Resposta de erro padrão da API: {@code application/problem+json} (RFC 9457). */
    private static Schema<?> problema() {
        return new ObjectSchema()
                .description("Erro no formato RFC 9457 (application/problem+json). Nunca traz stacktrace.")
                .addProperty("type", new StringSchema().format("uri")
                        .example("https://semanexus.demo/problemas/validacao"))
                .addProperty("title", new StringSchema().example("Dados inválidos"))
                .addProperty("status", new IntegerSchema().format("int32").example(400))
                .addProperty("detail", new StringSchema().example("Um ou mais campos são inválidos."))
                .addProperty("instance", new StringSchema().format("uri-reference").example("/api/demandas"))
                .addProperty("errors", new ArraySchema()
                        .description("Somente em validação (400): um item por campo inválido")
                        .items(new Schema<>().$ref("#/components/schemas/ErroCampo")))
                .required(List.of("type", "title", "status"));
    }
}
