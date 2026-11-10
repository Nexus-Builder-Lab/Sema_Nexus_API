package br.com.nexusbuilderlab.semanexus.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respostas 401 (sem token/token inválido) e 403 (perfil sem permissão) geradas pelo Spring Security
 * no mesmo formato RFC 9457 ({@code application/problem+json}) do restante da API.
 */
public class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final URI TYPE_NAO_AUTENTICADO = URI.create("https://semanexus.demo/problemas/nao-autenticado");
    static final URI TYPE_ACESSO_NEGADO = URI.create("https://semanexus.demo/problemas/acesso-negado");

    private final ObjectMapper mapper;

    public ProblemDetailSecurityHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        escrever(request, response, HttpStatus.UNAUTHORIZED, "Não autenticado",
                "Autenticação necessária: informe um token válido no cabeçalho Authorization (Bearer).",
                TYPE_NAO_AUTENTICADO);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        escrever(request, response, HttpStatus.FORBIDDEN, "Acesso negado",
                "Seu perfil não tem permissão para executar esta operação.", TYPE_ACESSO_NEGADO);
    }

    private void escrever(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                          String titulo, String detalhe, URI tipo) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detalhe);
        problem.setTitle(titulo);
        problem.setType(tipo);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
