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
 * associações em consultas de log. A gravação centralizada e a consulta chegam na Issue #14; a cadeia SHA-256
 * e os valores antes/depois, na Issue #148.
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

    /** Valores anteriores dos campos alterados (JSON objeto; só nas edições, Issue #148). */
    @Column(name = "VALOR_ANTERIOR", length = 4000, updatable = false)
    private String valorAnterior;

    /** Valores novos dos campos alterados (JSON objeto; só nas edições, Issue #148). */
    @Column(name = "VALOR_NOVO", length = 4000, updatable = false)
    private String valorNovo;

    /** Selagem da cadeia (gravada pelo {@link SeladorAuditoria} após o commit; nunca pelo JPA). */
    @Column(name = "SEQ_CADEIA", insertable = false, updatable = false)
    private Long seqCadeia;

    @Column(name = "HASH_ANTERIOR", length = 64, insertable = false, updatable = false)
    private String hashAnterior;

    @Column(name = "HASH_REGISTRO", length = 64, insertable = false, updatable = false)
    private String hashRegistro;

    protected Auditoria() {
    }

    public Auditoria(Long usuarioId, String acao, String entidade, String entidadeId, String detalhe) {
        this.usuarioId = usuarioId;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.detalhe = detalhe;
    }

    public Auditoria(Long usuarioId, String acao, String entidade, String entidadeId, String detalhe,
                     String valorAnterior, String valorNovo) {
        this(usuarioId, acao, entidade, entidadeId, detalhe);
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
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

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }

    public Long getSeqCadeia() {
        return seqCadeia;
    }

    public String getHashAnterior() {
        return hashAnterior;
    }

    public String getHashRegistro() {
        return hashRegistro;
    }
}
