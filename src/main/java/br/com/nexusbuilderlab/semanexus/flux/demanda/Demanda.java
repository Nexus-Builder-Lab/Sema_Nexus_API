package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.SQLRestriction;

/**
 * Demanda de fiscalização ambiental (dados fictícios na demonstração). Exclusão lógica por
 * {@code EXCLUIDO_EM}: registros excluídos nunca são carregados. "Atrasada" é derivada, não é status
 * ({@link #atrasada(LocalDate)}). As transições de status são feitas pelo service (máquina de estados em
 * {@code docs/ARCHITECTURE.md} §5).
 */
@Entity
@Table(name = "DEMANDA")
@SQLRestriction("EXCLUIDO_EM IS NULL")
public class Demanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "NUMERO", nullable = false, length = 20, updatable = false)
    private String numero;

    @Column(name = "TITULO", nullable = false, length = 200)
    private String titulo;

    @Lob
    @Column(name = "DESCRICAO", nullable = false)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "ORIGEM", nullable = false, length = 30)
    private Origem origem;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORIA", length = 40)
    private CategoriaFiscalizacao categoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MUNICIPIO_ID", nullable = false)
    private Municipio municipio;

    @Column(name = "LOCALIDADE", length = 200)
    private String localidade;

    @Column(name = "LATITUDE", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "LONGITUDE", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "DATA_RECEBIMENTO", nullable = false)
    private LocalDate dataRecebimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "PRIORIDADE", length = 10)
    private Prioridade prioridade;

    @Column(name = "PRAZO_SUGERIDO")
    private LocalDate prazoSugerido;

    @Column(name = "PRAZO")
    private LocalDate prazo;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private StatusDemanda status = StatusDemanda.PENDENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EQUIPE_ID")
    private Equipe equipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FISCAL_ID")
    private Usuario fiscal;

    @Column(name = "OBSERVACOES", length = 2000)
    private String observacoes;

    @Lob
    @Column(name = "RESULTADO_FINAL")
    private String resultadoFinal;

    @Column(name = "JUSTIFICATIVA_CONCLUSAO", length = 2000)
    private String justificativaConclusao;

    @Column(name = "CONCLUIDA_EM")
    private LocalDateTime concluidaEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CRIADO_POR", nullable = false, updatable = false)
    private Usuario criadoPor;

    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "ATUALIZADO_EM")
    private LocalDateTime atualizadoEm;

    @Column(name = "EXCLUIDO_EM")
    private LocalDateTime excluidoEm;

    protected Demanda() {
    }

    public Demanda(String numero, Usuario criadoPor) {
        this.numero = numero;
        this.criadoPor = criadoPor;
    }

    @PrePersist
    void aoCriar() {
        criadoEm = LocalDateTime.now();
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = LocalDateTime.now();
    }

    /** Derivada: prazo vencido (anterior a {@code hoje}) e demanda ainda não finalizada. */
    public boolean atrasada(LocalDate hoje) {
        return prazo != null && prazo.isBefore(hoje) && status != StatusDemanda.FINALIZADA;
    }

    /** Marca a exclusão lógica. */
    public void excluir() {
        excluidoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Origem getOrigem() {
        return origem;
    }

    public void setOrigem(Origem origem) {
        this.origem = origem;
    }

    public CategoriaFiscalizacao getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaFiscalizacao categoria) {
        this.categoria = categoria;
    }

    public Municipio getMunicipio() {
        return municipio;
    }

    public void setMunicipio(Municipio municipio) {
        this.municipio = municipio;
    }

    public String getLocalidade() {
        return localidade;
    }

    public void setLocalidade(String localidade) {
        this.localidade = localidade;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public LocalDate getDataRecebimento() {
        return dataRecebimento;
    }

    public void setDataRecebimento(LocalDate dataRecebimento) {
        this.dataRecebimento = dataRecebimento;
    }

    public Prioridade getPrioridade() {
        return prioridade;
    }

    public void setPrioridade(Prioridade prioridade) {
        this.prioridade = prioridade;
    }

    public LocalDate getPrazoSugerido() {
        return prazoSugerido;
    }

    public void setPrazoSugerido(LocalDate prazoSugerido) {
        this.prazoSugerido = prazoSugerido;
    }

    public LocalDate getPrazo() {
        return prazo;
    }

    public void setPrazo(LocalDate prazo) {
        this.prazo = prazo;
    }

    public StatusDemanda getStatus() {
        return status;
    }

    public void setStatus(StatusDemanda status) {
        this.status = status;
    }

    public Equipe getEquipe() {
        return equipe;
    }

    public void setEquipe(Equipe equipe) {
        this.equipe = equipe;
    }

    public Usuario getFiscal() {
        return fiscal;
    }

    public void setFiscal(Usuario fiscal) {
        this.fiscal = fiscal;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public String getResultadoFinal() {
        return resultadoFinal;
    }

    public void setResultadoFinal(String resultadoFinal) {
        this.resultadoFinal = resultadoFinal;
    }

    public String getJustificativaConclusao() {
        return justificativaConclusao;
    }

    public void setJustificativaConclusao(String justificativaConclusao) {
        this.justificativaConclusao = justificativaConclusao;
    }

    public LocalDateTime getConcluidaEm() {
        return concluidaEm;
    }

    public void setConcluidaEm(LocalDateTime concluidaEm) {
        this.concluidaEm = concluidaEm;
    }

    public Usuario getCriadoPor() {
        return criadoPor;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public LocalDateTime getExcluidoEm() {
        return excluidoEm;
    }
}
