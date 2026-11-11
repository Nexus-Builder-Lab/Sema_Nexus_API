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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro (simulado) de fiscalização, 1:1 com a demanda no MVP. A Issue #11 só mapeia a tabela e exibe o
 * registro no detalhe da demanda quando existir; a criação é da Issue #13.
 */
@Entity
@Table(name = "FISCALIZACAO")
public class Fiscalizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DEMANDA_ID", nullable = false, updatable = false)
    private Demanda demanda;

    @Column(name = "DATA", nullable = false)
    private LocalDate data;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EQUIPE_ID", nullable = false)
    private Equipe equipe;

    @Lob
    @Column(name = "ATIVIDADES", nullable = false)
    private String atividades;

    @Lob
    @Column(name = "RESULTADO", nullable = false)
    private String resultado;

    @Lob
    @Column(name = "EVIDENCIAS", nullable = false)
    private String evidencias;

    @Lob
    @Column(name = "RECOMENDACOES")
    private String recomendacoes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "REGISTRADO_POR", nullable = false, updatable = false)
    private Usuario registradoPor;

    @Column(name = "REGISTRADO_EM", nullable = false, updatable = false)
    private LocalDateTime registradoEm;

    protected Fiscalizacao() {
    }

    public Fiscalizacao(Demanda demanda, LocalDate data, Equipe equipe, String atividades, String resultado,
                        String evidencias, String recomendacoes, Usuario registradoPor) {
        this.demanda = demanda;
        this.data = data;
        this.equipe = equipe;
        this.atividades = atividades;
        this.resultado = resultado;
        this.evidencias = evidencias;
        this.recomendacoes = recomendacoes;
        this.registradoPor = registradoPor;
    }

    /** Reenvio após devolução: mantém o registro 1:1 e substitui o conteúdo. */
    public void atualizar(LocalDate data, String atividades, String resultado, String evidencias,
                          String recomendacoes) {
        this.data = data;
        this.atividades = atividades;
        this.resultado = resultado;
        this.evidencias = evidencias;
        this.recomendacoes = recomendacoes;
    }

    @PrePersist
    void aoCriar() {
        registradoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Demanda getDemanda() {
        return demanda;
    }

    public LocalDate getData() {
        return data;
    }

    public Equipe getEquipe() {
        return equipe;
    }

    public String getAtividades() {
        return atividades;
    }

    public String getResultado() {
        return resultado;
    }

    public String getEvidencias() {
        return evidencias;
    }

    public String getRecomendacoes() {
        return recomendacoes;
    }

    public Usuario getRegistradoPor() {
        return registradoPor;
    }

    public LocalDateTime getRegistradoEm() {
        return registradoEm;
    }
}
