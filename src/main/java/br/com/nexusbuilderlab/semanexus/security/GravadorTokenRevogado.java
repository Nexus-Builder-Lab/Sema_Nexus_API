package br.com.nexusbuilderlab.semanexus.security;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava a revogação numa transação própria ({@code REQUIRES_NEW}, Issue #134): se dois logouts simultâneos do mesmo
 * token colidirem na PK, só esta transação curta é desfeita — a do logout (auditoria) segue intacta. Bean separado para
 * a propagação passar pelo proxy do Spring.
 */
@Component
class GravadorTokenRevogado {

    private final TokenRevogadoRepository repositorio;

    GravadorTokenRevogado(TokenRevogadoRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** true quando esta chamada inseriu o registro; false quando o token já estava revogado. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean gravar(String jti, Long usuarioId, LocalDateTime expiraEm, LocalDateTime revogadoEm) {
        return repositorio.inserirSeAusente(jti, usuarioId, expiraEm, revogadoEm) > 0;
    }
}
