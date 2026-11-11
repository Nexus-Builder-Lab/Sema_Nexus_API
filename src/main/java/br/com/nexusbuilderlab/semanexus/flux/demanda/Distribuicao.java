package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Registro de uma (re)distribuição da demanda a uma equipe e a um fiscal. */
@Entity
@Table(name = "DISTRIBUICAO")
public class Distribuicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DEMANDA_ID", nullable = false, updatable = false)
    private Demanda demanda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EQUIPE_ID", nullable = false, updatable = false)
    private Equipe equipe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FISCAL_ID", nullable = false, updatable = false)
    private Usuario fiscal;

    @Column(name = "DATA", nullable = false, updatable = false)
    private LocalDateTime data;

    @Column(name = "PRAZO", nullable = false, updatable = false)
    private LocalDate prazo;

    @Column(name = "OBSERVACOES", length = 2000, updatable = false)
    private String observacoes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USUARIO_ID", nullable = false, updatable = false)
    private Usuario usuario;

    protected Distribuicao() {
    }

    public Distribuicao(Demanda demanda, Equipe equipe, Usuario fiscal, LocalDate prazo, String observacoes,
                        Usuario usuario) {
        this.demanda = demanda;
        this.equipe = equipe;
        this.fiscal = fiscal;
        this.prazo = prazo;
        this.observacoes = observacoes;
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

    public Equipe getEquipe() {
        return equipe;
    }

    public Usuario getFiscal() {
        return fiscal;
    }

    public LocalDateTime getData() {
        return data;
    }

    public LocalDate getPrazo() {
        return prazo;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public Usuario getUsuario() {
        return usuario;
    }
}
