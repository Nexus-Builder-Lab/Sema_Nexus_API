package br.com.nexusbuilderlab.semanexus.security;

import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lê {@code Authorization: Bearer <jwt>}, valida o token e popula o contexto de segurança.
 * Token ausente, inválido, expirado ou de usuário inexistente/inativo deixa o contexto vazio:
 * o ponto de entrada do Spring Security responde 401. Não é um {@code @Component} de propósito —
 * é registrado somente dentro da cadeia de segurança (evita registro duplicado como filtro de servlet).
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarios;

    public JwtAuthFilter(JwtService jwtService, UsuarioRepository usuarios) {
        this.jwtService = jwtService;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecalho = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecalho != null
                && cabecalho.regionMatches(true, 0, PREFIXO, 0, PREFIXO.length())
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = cabecalho.substring(PREFIXO.length()).trim();
            jwtService.validar(token)
                    .flatMap(t -> usuarios.findById(t.usuarioId()))
                    .filter(u -> u.isAtivo())
                    .ifPresent(u -> {
                        // O perfil vem do banco (não do token), então mudanças de perfil valem de imediato.
                        var principal = UsuarioAutenticado.de(u);
                        var autenticacao = UsernamePasswordAuthenticationToken.authenticated(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getPerfil().name())));
                        autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(autenticacao);
                    });
        }
        chain.doFilter(request, response);
    }
}
