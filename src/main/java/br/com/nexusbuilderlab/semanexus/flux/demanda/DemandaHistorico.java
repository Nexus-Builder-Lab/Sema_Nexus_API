package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/** Evento da linha do tempo da demanda (append-only). */
@Entity
@Immutable
@Table(name = "DEMANDA_HISTORICO")
public class DemandaHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DEMANDA_ID", nullable = false, updatable = false)
    private Demanda demanda;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false, length = 20, updatable = false)
    private TipoHistorico tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS_ANTERIOR", length = 30, updatable = false)
    private StatusDemanda statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS_NOVO", length = 30, updatable = false)
    private StatusDemanda statusNovo;

    @Column(name = "TEXTO", length = 2000, updatable = false)
    private String texto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USUARIO_ID", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "DATA", nullable = false, updatable = false)
    private LocalDateTime data;

    protected DemandaHistorico() {
    }

    public DemandaHistorico(Demanda demanda, TipoHistorico tipo, StatusDemanda statusAnterior,
                            StatusDemanda statusNovo, String texto, Usuario usuario) {
        this.demanda = demanda;
        this.tipo = tipo;
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.texto = texto;
        this.usuario = usuario;
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

    public Demanda getDemanda() {
        return demanda;
    }

    public TipoHistorico getTipo() {
        return tipo;
    }

    public StatusDemanda getStatusAnterior() {
        return statusAnterior;
    }

    public StatusDemanda getStatusNovo() {
        return statusNovo;
    }

    public String getTexto() {
        return texto;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public LocalDateTime getData() {
        return data;
    }
}
