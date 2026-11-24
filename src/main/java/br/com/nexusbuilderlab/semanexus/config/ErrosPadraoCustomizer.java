package br.com.nexusbuilderlab.semanexus.config;

import br.com.nexusbuilderlab.semanexus.common.openapi.ContratoPendente;
import br.com.nexusbuilderlab.semanexus.security.LimiteRequisicoesFilter;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.reflect.Method;
import java.util.Set;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

/**
 * Documenta em todas as operações as respostas de erro padrão da API ({@code application/problem+json},
 * RFC 9457), a partir do que o método declara — sem repetir {@code @ApiResponse} em cada endpoint:
 *
 * <ul>
 *   <li>401 em tudo que exige token; 403 quando há {@code @PreAuthorize};</li>
 *   <li>400 quando a operação recebe corpo, parâmetros ou paginação; 404 com {@code @PathVariable};</li>
 *   <li>409 em operações que alteram recurso identificado por caminho (transição inválida, vínculo duplicado);</li>
 *   <li>413 e 415 em uploads multipart; 501 em {@link ContratoPendente};</li>
 *   <li>429 (com {@code Retry-After}) nos endpoints com limite de requisições (login, IA e upload — Issue #134).</li>
 * </ul>
 *
 * Respostas já declaradas explicitamente com {@code @ApiResponse} mantêm a descrição e passam a usar o schema de erro.
 */
@Configuration
public class ErrosPadraoCustomizer {

    static final String PROBLEM_JSON = "application/problem+json";
    private static final Set<RequestMethod> ESCRITA =
            Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    @Bean
    OperationCustomizer errosPadrao() {
        return ErrosPadraoCustomizer::aplicar;
    }

    static Operation aplicar(Operation operation, HandlerMethod handler) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        ApiResponses respostas = operation.getResponses();
        Method metodo = handler.getMethod();
        boolean publico = operation.getSecurity() != null && operation.getSecurity().isEmpty();
        boolean temParametros = false;
        boolean temCaminho = false;
        boolean multipart = false;
        for (MethodParameter p : handler.getMethodParameters()) {
            if (p.hasParameterAnnotation(PathVariable.class)) {
                temCaminho = true;
            } else if (p.hasParameterAnnotation(RequestBody.class)
                    || p.hasParameterAnnotation(ModelAttribute.class)
                    || p.hasParameterAnnotation(RequestParam.class)
                    || p.hasParameterAnnotation(org.springdoc.core.annotations.ParameterObject.class)
                    || Pageable.class.isAssignableFrom(p.getParameterType())) {
                temParametros = true;
            }
        }
        RequestMapping mapeamento = AnnotatedElementUtils.findMergedAnnotation(metodo, RequestMapping.class);
        if (mapeamento != null) {
            for (String consome : mapeamento.consumes()) {
                multipart |= consome.startsWith("multipart/");
            }
        }
        boolean escrita = mapeamento != null && mapeamento.method().length > 0
                && ESCRITA.contains(mapeamento.method()[0]);

        if (temParametros || multipart) {
            padrao(respostas, HttpStatus.BAD_REQUEST, "Dados inválidos; os problemas por campo vêm em `errors`.");
        }
        if (!publico) {
            padrao(respostas, HttpStatus.UNAUTHORIZED, "Sem token, token inválido/expirado ou usuário inativo.");
        }
        if (AnnotatedElementUtils.hasAnnotation(metodo, PreAuthorize.class)) {
            padrao(respostas, HttpStatus.FORBIDDEN, "Perfil sem permissão para a operação.");
        }
        if (temCaminho) {
            padrao(respostas, HttpStatus.NOT_FOUND, "Recurso inexistente (ou excluído logicamente).");
        }
        if (escrita && temCaminho) {
            padrao(respostas, HttpStatus.CONFLICT, "Conflito com o estado atual (transição inválida, vínculo duplicado).");
        }
        if (multipart) {
            padrao(respostas, HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo maior que o limite (10 MB).");
            padrao(respostas, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de arquivo não permitido ou conteúdo incompatível com a extensão.");
        }

        if (mapeamento != null && comLimiteDeRequisicoes(handler, mapeamento)) {
            padrao(respostas, HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de requisições excedido; aguarde os segundos do cabeçalho `Retry-After`.");
            respostas.get("429").addHeaderObject("Retry-After", new Header()
                    .description("Segundos até a próxima janela do limite.")
                    .schema(new Schema<Integer>().type("integer")));
        }

        ContratoPendente pendente = AnnotatedElementUtils.findMergedAnnotation(metodo, ContratoPendente.class);
        if (pendente == null) {
            pendente = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), ContratoPendente.class);
        }
        if (pendente != null) {
            padrao(respostas, HttpStatus.NOT_IMPLEMENTED,
                    "Contrato publicado, ainda não implementado (Issue #" + pendente.issue() + ").");
            String aviso = "**Contrato publicado — ainda não implementado (Issue #" + pendente.issue()
                    + "); responde 501 até lá.**";
            operation.setDescription(operation.getDescription() == null
                    ? aviso
                    : aviso + "\n\n" + operation.getDescription());
            operation.addExtension("x-implementado", false);
            operation.addExtension("x-issue", pendente.issue());
        }
        // Toda resposta de erro (4xx/5xx) é problem+json. O springdoc preenche @ApiResponse escritas à mão com o
        // tipo de retorno do método; aqui isso é substituído pelo schema de erro.
        respostas.forEach((codigo, resposta) -> {
            if (codigo.matches("[45]\\d\\d")) {
                resposta.setContent(conteudoProblema());
            }
        });
        return operation;
    }

    /** Mesma regra do {@link LimiteRequisicoesFilter}, aplicada aos caminhos declarados no controller. */
    private static boolean comLimiteDeRequisicoes(HandlerMethod handler, RequestMapping mapeamento) {
        if (mapeamento.method().length == 0 || mapeamento.method()[0] != RequestMethod.POST) {
            return false;
        }
        RequestMapping daClasse = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequestMapping.class);
        String[] bases = daClasse == null || daClasse.path().length == 0 ? new String[] {""} : daClasse.path();
        String[] caminhos = mapeamento.path().length == 0 ? new String[] {""} : mapeamento.path();
        for (String base : bases) {
            for (String caminho : caminhos) {
                if (LimiteRequisicoesFilter.categoria("POST", base + caminho) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Content conteudoProblema() {
        return new Content().addMediaType(PROBLEM_JSON,
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/Problema")));
    }

    /** Declara a resposta de erro padrão, sem sobrescrever a descrição de uma já declarada à mão. */
    private static void padrao(ApiResponses respostas, HttpStatus status, String descricao) {
        respostas.addApiResponse(String.valueOf(status.value()),
                respostas.getOrDefault(String.valueOf(status.value()), new ApiResponse().description(descricao)));
    }
}
