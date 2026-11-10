package br.com.nexusbuilderlab.semanexus.common.exception;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tratamento centralizado de erros. Todas as respostas de erro seguem a RFC 9457
 * ({@code application/problem+json}) e nunca expõem stacktrace ou mensagens internas.
 *
 * <p>Exceções padrão do Spring MVC (404 de rota, 405, 415, JSON malformado etc.) são tratadas pela
 * classe base, que já produz {@link ProblemDetail}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final URI TYPE_VALIDACAO = URI.create("https://semanexus.demo/problemas/validacao");
    static final URI TYPE_NAO_ENCONTRADO = URI.create("https://semanexus.demo/problemas/nao-encontrado");
    static final URI TYPE_CONFLITO = URI.create("https://semanexus.demo/problemas/conflito");
    static final URI TYPE_CREDENCIAIS = URI.create("https://semanexus.demo/problemas/credenciais-invalidas");
    static final URI TYPE_USUARIO_INATIVO = URI.create("https://semanexus.demo/problemas/usuario-inativo");
    static final URI TYPE_NAO_IMPLEMENTADO = URI.create("https://semanexus.demo/problemas/nao-implementado");
    static final URI TYPE_ERRO_INTERNO = URI.create("https://semanexus.demo/problemas/erro-interno");

    /** Erro de validação de um campo: nome do campo e mensagem legível. */
    public record ErroCampo(String campo, String mensagem) {}

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        List<ErroCampo> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErroCampo(fe.getField(), fe.getDefaultMessage()))
                .sorted(Comparator.comparing(ErroCampo::campo))
                .toList();
        ProblemDetail problem = validacao(erros);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<ErroCampo> erros = ex.getConstraintViolations().stream()
                .map(v -> new ErroCampo(ultimoNo(v.getPropertyPath().toString()), v.getMessage()))
                .sorted(Comparator.comparing(ErroCampo::campo))
                .toList();
        return validacao(erros);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso não encontrado");
        problem.setType(TYPE_NAO_ENCONTRADO);
        return problem;
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail handleConflito(ConflitoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflito com o estado atual do recurso");
        problem.setType(TYPE_CONFLITO);
        return problem;
    }

    /** Endpoint com contrato publicado e implementação ainda pendente (Issue indicada no detalhe). */
    @ExceptionHandler(ContratoPendenteException.class)
    public ProblemDetail handleContratoPendente(ContratoPendenteException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, ex.getMessage());
        problem.setTitle("Endpoint ainda não implementado");
        problem.setType(TYPE_NAO_IMPLEMENTADO);
        problem.setProperty("issue", ex.getIssue());
        return problem;
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ProblemDetail handleCredenciaisInvalidas(CredenciaisInvalidasException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Credenciais inválidas");
        problem.setType(TYPE_CREDENCIAIS);
        return problem;
    }

    @ExceptionHandler(UsuarioInativoException.class)
    public ProblemDetail handleUsuarioInativo(UsuarioInativoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Usuário inativo");
        problem.setType(TYPE_USUARIO_INATIVO);
        return problem;
    }

    /**
     * Erros de autenticação/autorização são repassados ao Spring Security, que responde 401/403.
     * Sem isso, o handler genérico abaixo os transformaria em 500.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void repassarParaSpringSecurity(RuntimeException ex) {
        throw ex;
    }

    /** Qualquer erro não previsto: registra no log e devolve uma mensagem genérica, sem detalhes internos. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleInesperado(Exception ex) {
        log.error("Erro não tratado", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro inesperado. Tente novamente ou contate o administrador.");
        problem.setTitle("Erro interno");
        problem.setType(TYPE_ERRO_INTERNO);
        return problem;
    }

    private static ProblemDetail validacao(List<ErroCampo> erros) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setTitle("Dados inválidos");
        problem.setType(TYPE_VALIDACAO);
        problem.setProperty("errors", erros);
        return problem;
    }

    private static String ultimoNo(String path) {
        int i = path.lastIndexOf('.');
        return i >= 0 ? path.substring(i + 1) : path;
    }
}
