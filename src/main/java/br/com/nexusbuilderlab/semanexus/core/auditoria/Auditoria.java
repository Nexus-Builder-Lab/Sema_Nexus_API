package br.com.nexusbuilderlab.semanexus.core.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/**
 * Registro de auditoria (append-only). O usuário é guardado só pelo ID para não carregar
 * associações em consultas de log. A gravação centralizada e a consulta chegam na Issue #14.
 */
@Entity
@Immutable
@Table(name = "AUDITORIA")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DATA", nullable = false, updatable = false)
    private LocalDateTime data;

    @Column(name = "USUARIO_ID", updatable = false)
    private Long usuarioId;

    @Column(name = "ACAO", nullable = false, length = 60, updatable = false)
    private String acao;

    @Column(name = "ENTIDADE", nullable = false, length = 60, updatable = false)
    private String entidade;

    @Column(name = "ENTIDADE_ID", length = 64, updatable = false)
    private String entidadeId;

    @Column(name = "DETALHE", length = 4000, updatable = false)
    private String detalhe;

    protected Auditoria() {
    }

    public Auditoria(Long usuarioId, String acao, String entidade, String entidadeId, String detalhe) {
        this.usuarioId = usuarioId;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.detalhe = detalhe;
    }

    @PrePersist
    void aoCriar() {
        if (data == null) {
            data = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getData() {
        return data;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getAcao() {
        return acao;
    }

    public String getEntidade() {
        return entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public String getDetalhe() {
        return detalhe;
    }
}
