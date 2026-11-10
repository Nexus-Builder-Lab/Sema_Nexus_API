package br.com.nexusbuilderlab.semanexus.core.auth;

import br.com.nexusbuilderlab.semanexus.common.exception.CredenciaisInvalidasException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.common.exception.UsuarioInativoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.AuthConfigResponse;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginRequest;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.LoginResponse;
import br.com.nexusbuilderlab.semanexus.core.auth.dto.UsuarioResumo;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import java.time.Clock;
import java.time.LocalDateTime;
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

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final AuditService auditoria;
    private final Environment ambiente;
    private final Clock clock;
    /** Hash descartável para gastar o mesmo tempo de BCrypt quando o e-mail não existe (evita enumeração por tempo). */
    private final String hashFalso;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwtService,
                       AuditService auditoria, Environment ambiente, Clock clock) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.auditoria = auditoria;
        this.ambiente = ambiente;
        this.clock = clock;
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

    /** O token é descartado no cliente; aqui só se registra a saída na auditoria. */
    @Transactional
    public void logout(Long usuarioId) {
        auditoria.registrarComo(usuarioId, "LOGOUT", ENTIDADE, String.valueOf(usuarioId), "Logout realizado.");
    }

    @Transactional(readOnly = true)
    public UsuarioResumo me(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .map(UsuarioResumo::de)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuário", usuarioId));
    }

    public AuthConfigResponse configuracao() {
        return new AuthConfigResponse(ambiente.acceptsProfiles(Profiles.of("demo")));
    }
}
