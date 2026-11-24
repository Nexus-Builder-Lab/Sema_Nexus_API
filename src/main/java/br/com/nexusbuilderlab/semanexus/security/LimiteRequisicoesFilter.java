package br.com.nexusbuilderlab.semanexus.security;

import br.com.nexusbuilderlab.semanexus.security.LimitadorRequisicoes.Categoria;
import br.com.nexusbuilderlab.semanexus.security.LimitadorRequisicoes.Decisao;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Aplica o {@link LimitadorRequisicoes} aos endpoints sensíveis (Issue #134): login (inclusive o demonstrativo), agentes
 * de IA (sugestão de triagem e perguntas ao DocIA) e envio de documento. Roda depois do {@link JwtAuthFilter}, para
 * contar por usuário quando há token. Excedido o limite: 429 {@code application/problem+json} com {@code Retry-After}.
 * Registrado só dentro da cadeia de segurança (como o {@link JwtAuthFilter}).
 */
public class LimiteRequisicoesFilter extends OncePerRequestFilter {

    static final URI TYPE_LIMITE = URI.create("https://semanexus.demo/problemas/limite-requisicoes");

    private static final Pattern LOGIN = Pattern.compile("^/api/auth/(login|demo/[^/]+)/?$");
    private static final Pattern IA = Pattern.compile("^/api/(demandas/[^/]+/triagem/sugestao|docia/perguntas)/?$");
    private static final Pattern UPLOAD = Pattern.compile("^/api/documentos/?$");
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private final LimitadorRequisicoes limitador;
    private final ObjectMapper mapper;

    public LimiteRequisicoesFilter(LimitadorRequisicoes limitador, ObjectMapper mapper) {
        this.limitador = limitador;
        this.mapper = mapper;
    }

    /**
     * Categoria do endpoint ou nula quando ele não tem limite próprio. Só POST é limitado. Também usada pelo
     * {@code ErrosPadraoCustomizer} para documentar o 429 no OpenAPI (uma única fonte da regra).
     */
    public static Categoria categoria(String metodo, String caminho) {
        if (!"POST".equalsIgnoreCase(metodo) || caminho == null) {
            return null;
        }
        if (LOGIN.matcher(caminho).matches()) {
            return Categoria.LOGIN;
        }
        if (IA.matcher(caminho).matches()) {
            return Categoria.IA;
        }
        if (UPLOAD.matcher(caminho).matches()) {
            return Categoria.UPLOAD;
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Categoria categoria = limitador.ativo() ? categoria(request.getMethod(), caminho(request)) : null;
        if (categoria != null) {
            Decisao decisao = limitador.consumir(categoria, chave(categoria, request));
            if (!decisao.permitido()) {
                recusar(request, response, categoria, decisao.retryAfterSegundos());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String caminho(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contexto = request.getContextPath();
        return contexto != null && !contexto.isEmpty() && uri.startsWith(contexto)
                ? uri.substring(contexto.length()) : uri;
    }

    /** Login sempre por IP (não há usuário); IA e upload por usuário autenticado (ou IP, sem token). */
    private static String chave(Categoria categoria, HttpServletRequest request) {
        if (categoria != Categoria.LOGIN) {
            var autenticacao = SecurityContextHolder.getContext().getAuthentication();
            if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado u) {
                return "u:" + u.id();
            }
        }
        return "ip:" + ipCliente(request);
    }

    /**
     * IP do cliente. Atrás do Nginx do Web (rede privada do Docker), vale o último endereço de {@code X-Forwarded-For}
     * — o que o próprio Nginx acrescentou; o cabeçalho só é considerado quando a conexão vem de endereço privado ou
     * local (proxy confiável), para um cliente externo não escolher o próprio IP.
     */
    static String ipCliente(HttpServletRequest request) {
        String remoto = request.getRemoteAddr();
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank() && proxyConfiavel(remoto)) {
            String[] partes = encaminhado.split(",");
            String ultimo = partes[partes.length - 1].strip();
            if (IP_LITERAL.matcher(ultimo).matches()) {
                return ultimo;
            }
        }
        return remoto == null ? "desconhecido" : remoto;
    }

    private static boolean proxyConfiavel(String endereco) {
        if (endereco == null || !IP_LITERAL.matcher(endereco).matches()) {
            return false;
        }
        try {
            InetAddress ip = InetAddress.getByName(endereco); // literal de IP: não consulta DNS
            return ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private void recusar(HttpServletRequest request, HttpServletResponse response, Categoria categoria,
                         long retryAfter) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas requisições em pouco tempo. Tente novamente em " + retryAfter + " segundo(s).");
        problem.setTitle("Limite de requisições excedido");
        problem.setType(TYPE_LIMITE);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("categoria", categoria.name());
        problem.setProperty("retryAfterSegundos", retryAfter);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
