package br.com.nexusbuilderlab.semanexus.core.auditoria;

import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gravação centralizada de auditoria (versão mínima da Issue #4, ampliada na Issue #14).
 * O {@code detalhe} nunca deve conter senha, token ou outro dado sensível.
 */
@Service
public class AuditService {

    static final int LIMITE_DETALHE = 4000;

    private final AuditoriaRepository repositorio;

    public AuditService(AuditoriaRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Registra a ação em nome do usuário autenticado no contexto de segurança (ou anônimo, se não houver). */
    @Transactional
    public void registrar(String acao, String entidade, String entidadeId, String detalhe) {
        registrarComo(usuarioAtualId(), acao, entidade, entidadeId, detalhe);
    }

    /** Registra a ação em nome do usuário informado (ex.: login, quando o contexto ainda está vazio). */
    @Transactional
    public void registrarComo(Long usuarioId, String acao, String entidade, String entidadeId, String detalhe) {
        String texto = detalhe != null && detalhe.length() > LIMITE_DETALHE
                ? detalhe.substring(0, LIMITE_DETALHE)
                : detalhe;
        repositorio.save(new Auditoria(usuarioId, acao, entidade, entidadeId, texto));
    }

    private static Long usuarioAtualId() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado u) {
            return u.id();
        }
        return null;
    }
}
