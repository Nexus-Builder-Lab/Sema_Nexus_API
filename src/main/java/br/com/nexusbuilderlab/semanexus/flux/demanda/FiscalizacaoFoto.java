package br.com.nexusbuilderlab.semanexus.flux.demanda;

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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Foto de evidência da fiscalização (Issue #145). O arquivo fica no volume ({@code STORAGE_DIR}); aqui só a chave
 * opaca, o SHA-256 e os metadados. EXIF é informativo: nunca participa de autorização.
 */
@Entity
@Table(name = "FISCALIZACAO_FOTO")
public class FiscalizacaoFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DEMANDA_ID", nullable = false, updatable = false)
    private Demanda demanda;

    @Column(name = "CHAVE_ARQUIVO", nullable = false, updatable = false)
    private String chaveArquivo;

    @Column(name = "SHA256", nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "MIME", nullable = false, updatable = false)
    private String mime;

    @Column(name = "TAMANHO", nullable = false, updatable = false)
    private long tamanho;

    @Column(name = "NOME_ORIGINAL", nullable = false, updatable = false)
    private String nomeOriginal;

    @Column(name = "EXIF_DATA_HORA", updatable = false)
    private LocalDateTime exifDataHora;

    @Column(name = "EXIF_LATITUDE", updatable = false)
    private BigDecimal exifLatitude;

    @Column(name = "EXIF_LONGITUDE", updatable = false)
    private BigDecimal exifLongitude;

    @Column(name = "DISTANCIA_KM", updatable = false)
    private BigDecimal distanciaKm;

    @Column(name = "GPS_FORA_MT", nullable = false, updatable = false)
    private boolean gpsForaMt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "AUTOR_ID", nullable = false, updatable = false)
    private Usuario autor;

    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    protected FiscalizacaoFoto() {
    }

    @SuppressWarnings("java:S107")
    public FiscalizacaoFoto(Demanda demanda, String chaveArquivo, String sha256, String mime, long tamanho,
                            String nomeOriginal, LocalDateTime exifDataHora, BigDecimal exifLatitude,
                            BigDecimal exifLongitude, BigDecimal distanciaKm, boolean gpsForaMt, Usuario autor) {
        this.demanda = demanda;
        this.chaveArquivo = chaveArquivo;
        this.sha256 = sha256;
        this.mime = mime;
        this.tamanho = tamanho;
        this.nomeOriginal = nomeOriginal;
        this.exifDataHora = exifDataHora;
        this.exifLatitude = exifLatitude;
        this.exifLongitude = exifLongitude;
        this.distanciaKm = distanciaKm;
        this.gpsForaMt = gpsForaMt;
        this.autor = autor;
    }

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Demanda getDemanda() {
        return demanda;
    }

    public String getChaveArquivo() {
        return chaveArquivo;
    }

    public String getSha256() {
        return sha256;
    }

    public String getMime() {
        return mime;
    }

    public long getTamanho() {
        return tamanho;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public LocalDateTime getExifDataHora() {
        return exifDataHora;
    }

    public BigDecimal getExifLatitude() {
        return exifLatitude;
    }

    public BigDecimal getExifLongitude() {
        return exifLongitude;
    }

    public BigDecimal getDistanciaKm() {
        return distanciaKm;
    }

    public boolean isGpsForaMt() {
        return gpsForaMt;
    }

    public Usuario getAutor() {
        return autor;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
