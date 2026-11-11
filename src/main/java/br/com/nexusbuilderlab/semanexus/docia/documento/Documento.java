package br.com.nexusbuilderlab.semanexus.docia.documento;

import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Metadados do documento; o arquivo fica no volume ({@code STORAGE_KEY}) e o texto extraído em CLOB. */
@Entity
@Table(name = "DOCUMENTO")
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "TITULO", nullable = false)
    private String titulo;

    @Column(name = "DESCRICAO")
    private String descricao;

    @Column(name = "CATEGORIA", nullable = false)
    private String categoria;

    @Column(name = "TIPO", nullable = false)
    private String tipo;

    @Column(name = "DATA_DOCUMENTO", nullable = false)
    private LocalDate dataDocumento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MUNICIPIO_ID")
    private Municipio municipio;

    @Column(name = "PALAVRAS_CHAVE")
    private String palavrasChave;

    @Column(name = "NOME_ARQUIVO", nullable = false)
    private String nomeArquivo;

    @Column(name = "MIME", nullable = false)
    private String mime;

    @Column(name = "TAMANHO", nullable = false)
    private long tamanho;

    @Column(name = "STORAGE_KEY", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "SHA256", nullable = false, length = 64)
    private String sha256;

    @Lob
    @Column(name = "TEXTO_EXTRAIDO")
    private String textoExtraido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "RESPONSAVEL_ID", nullable = false, updatable = false)
    private Usuario responsavel;

    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "ATUALIZADO_EM")
    private LocalDateTime atualizadoEm;

    @Column(name = "EXCLUIDO_EM")
    private LocalDateTime excluidoEm;

    protected Documento() {
    }

    public Documento(String titulo, String descricao, String categoria, String tipo, LocalDate dataDocumento,
                     Municipio municipio, String palavrasChave, String nomeArquivo, String mime, long tamanho,
                     String storageKey, String sha256, String textoExtraido, Usuario responsavel) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.categoria = categoria;
        this.tipo = tipo;
        this.dataDocumento = dataDocumento;
        this.municipio = municipio;
        this.palavrasChave = palavrasChave;
        this.nomeArquivo = nomeArquivo;
        this.mime = mime;
        this.tamanho = tamanho;
        this.storageKey = storageKey;
        this.sha256 = sha256;
        this.textoExtraido = textoExtraido;
        this.responsavel = responsavel;
    }

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescricao() { return descricao; }
    public String getCategoria() { return categoria; }
    public String getTipo() { return tipo; }
    public LocalDate getDataDocumento() { return dataDocumento; }
    public Municipio getMunicipio() { return municipio; }
    public String getPalavrasChave() { return palavrasChave; }
    public String getNomeArquivo() { return nomeArquivo; }
    public String getMime() { return mime; }
    public long getTamanho() { return tamanho; }
    public String getStorageKey() { return storageKey; }
    public String getSha256() { return sha256; }
    public String getTextoExtraido() { return textoExtraido; }
    public Usuario getResponsavel() { return responsavel; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public LocalDateTime getExcluidoEm() { return excluidoEm; }
}
