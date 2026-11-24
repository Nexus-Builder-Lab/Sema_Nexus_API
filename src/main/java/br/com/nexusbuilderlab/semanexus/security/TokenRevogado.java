package br.com.nexusbuilderlab.semanexus.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Token JWT revogado no logout (Issue #134). Vale só até {@code expiraEm}; depois é removido pela limpeza diária. */
@Entity
@Table(name = "TOKEN_REVOGADO")
public class TokenRevogado {

    @Id
    @Column(name = "JTI", length = 64, nullable = false, updatable = false)
    private String jti;

    @Column(name = "USUARIO_ID", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "EXPIRA_EM", nullable = false, updatable = false)
    private LocalDateTime expiraEm;

    @Column(name = "REVOGADO_EM", nullable = false, updatable = false)
    private LocalDateTime revogadoEm;

    protected TokenRevogado() {
    }

    public TokenRevogado(String jti, Long usuarioId, LocalDateTime expiraEm, LocalDateTime revogadoEm) {
        this.jti = jti;
        this.usuarioId = usuarioId;
        this.expiraEm = expiraEm;
        this.revogadoEm = revogadoEm;
    }

    public String getJti() {
        return jti;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public LocalDateTime getExpiraEm() {
        return expiraEm;
    }

    public LocalDateTime getRevogadoEm() {
        return revogadoEm;
    }
}
