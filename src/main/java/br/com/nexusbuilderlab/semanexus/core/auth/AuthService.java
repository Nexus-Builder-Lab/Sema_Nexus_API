package br.com.nexusbuilderlab.semanexus.core.auth;

import br.com.nexusbuilderlab.semanexus.common.exception.CredenciaisInvalidasException;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.common.exception.UsuarioInativoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.AuthConfigResponse;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginRequest;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginResponse;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.UsuarioResumo;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.PerfilDemo;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.RevogacaoTokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regras de autenticação: login, logout (auditoria), usuário corrente e configuração pública de login. */
@Service
public class AuthService {

    static final String ENTIDADE = "USUARIO";

    private static final List<PerfilDemo> PERFIS_DEMO = List.of(
            new PerfilDemo(Perfil.ADMIN, "Administrador", "Gerencia usuários, equipes e auditoria."),
            new PerfilDemo(Perfil.GESTOR, "Gestor", "Acompanha demandas, equipes e indicadores."),
            new PerfilDemo(Perfil.FISCAL, "Fiscal", "Executa fiscalizações e registra vistorias."),
            new PerfilDemo(Perfil.ANALISTA, "Analista", "Cadastra e distribui demandas; analisa documentos."),
            new PerfilDemo(Perfil.CONSULTA, "Consulta", "Acesso somente leitura aos painéis."));

    /**
     * Conta preferida por perfil no login demonstrativo (Issue #112): o fiscal1 (equipes Alfa–Delta) ficou sem
     * demandas desde os núcleos regionais (#91); o fiscal do Núcleo Cuiabá tem demandas em vários estágios.
     * Sem a conta preferida (ou inativa), vale o primeiro usuário demonstrativo do perfil.
     */
    static final Map<Perfil, String> CONTA_DEMO_PREFERIDA = Map.of(Perfil.FISCAL, "fiscal.cba1@demo.semaalerta.local");

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final AuditService auditoria;
    private final Environment ambiente;
    private final Clock clock;
    private final RevogacaoTokenService revogacao;
    /** Hash descartável para gastar o mesmo tempo de BCrypt quando o e-mail não existe (evita enumeração por tempo). */
    private final String hashFalso;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwtService,
                       AuditService auditoria, Environment ambiente, Clock clock, RevogacaoTokenService revogacao) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.auditoria = auditoria;
        this.ambiente = ambiente;
        this.clock = clock;
        this.revogacao = revogacao;
        this.hashFalso = encoder.encode("senha-descartavel-sem-uso");
    }

    @Transactional
    public LoginResponse login(LoginRequest pedido) {
        Optional<Usuario> encontrado = usuarios.findByEmail(Usuario.normalizarEmail(pedido.email()));
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFalso);
        boolean senhaConfere = encoder.matches(pedido.senha(), hash);
        if (encontrado.isEmpty() || !senhaConfere) {
            throw new CredenciaisInvalidasException();
        }
        Usuario usuario = encontrado.get();
        // A situação só é revelada depois de a senha conferir (não permite descobrir e-mails cadastrados).
        if (!usuario.isAtivo()) {
            throw new UsuarioInativoException();
        }
        usuario.setUltimoAcesso(LocalDateTime.now(clock));
        auditoria.registrarComo(usuario.getId(), "LOGIN", ENTIDADE, String.valueOf(usuario.getId()), "Login realizado.");
        return new LoginResponse(jwtService.gerar(usuario), jwtService.expiresInSegundos(), UsuarioResumo.de(usuario));
    }

    /**
     * Revoga o token na hora (Issue #134: o {@code jti} vai para TOKEN_REVOGADO e o mesmo token passa a receber 401) e
     * registra a saída na auditoria. O cliente também descarta o token.
     */
    @Transactional
    public void logout(Long usuarioId, String jti, Instant expiraEm) {
        revogacao.revogar(jti, usuarioId, expiraEm);
        auditoria.registrarComo(usuarioId, "LOGOUT", ENTIDADE, String.valueOf(usuarioId), "Logout realizado.");
    }

    @Transactional(readOnly = true)
    public UsuarioResumo me(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .map(UsuarioResumo::de)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuário", usuarioId));
    }

    public AuthConfigResponse configuracao() {
        boolean demo = ambiente.acceptsProfiles(Profiles.of("demo"));
        return demo ? new AuthConfigResponse(true, PERFIS_DEMO) : new AuthConfigResponse(false);
    }

    /** Login do Modo demonstração (só exposto pelo AuthDemoController, profile demo). Não envolve senha. */
    @Transactional
    public LoginResponse loginDemo(String perfilTexto) {
        Perfil perfil = interpretarPerfil(perfilTexto);
        Usuario usuario = Optional.ofNullable(CONTA_DEMO_PREFERIDA.get(perfil))
                .flatMap(usuarios::findByEmail)
                .filter(u -> u.isDemo() && u.isAtivo() && u.getPerfil() == perfil)
                .or(() -> usuarios.findFirstByPerfilAndDemoTrueAndAtivoTrueOrderByIdAsc(perfil))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Nenhum usuário demonstrativo ativo para o perfil " + perfil + "."));
        usuario.setUltimoAcesso(LocalDateTime.now(clock));
        auditoria.registrarComo(usuario.getId(), "LOGIN", ENTIDADE, String.valueOf(usuario.getId()),
                "Login realizado (modo demonstração).");
        return new LoginResponse(jwtService.gerar(usuario), jwtService.expiresInSegundos(), UsuarioResumo.de(usuario));
    }

    private static Perfil interpretarPerfil(String texto) {
        try {
            return Perfil.valueOf(texto == null ? "" : texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DadosInvalidosException("perfil",
                    "Perfil inválido. Use ADMIN, GESTOR, FISCAL, ANALISTA ou CONSULTA.");
        }
    }
}
