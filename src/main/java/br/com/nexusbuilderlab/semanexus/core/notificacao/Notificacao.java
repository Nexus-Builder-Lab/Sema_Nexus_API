package br.com.nexusbuilderlab.semanexus.core.notificacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Notificação destinada a um único usuário; {@code chave} é única por usuário (anti-duplicidade). */
@Entity
@Table(name = "NOTIFICACAO")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "USUARIO_ID", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false, length = 30)
    private TipoNotificacao tipo;

    @Column(name = "TITULO", nullable = false, length = 200)
    private String titulo;

    @Column(name = "MENSAGEM", nullable = false, length = 1000)
    private String mensagem;

    @Column(name = "DEMANDA_ID")
    private Long demandaId;

    @Column(name = "CHAVE", nullable = false, length = 120)
    private String chave;

    @Column(name = "LIDA", nullable = false)
    private boolean lida;

    @Column(name = "CRIADA_EM", nullable = false, updatable = false)
    private LocalDateTime criadaEm;

    @Column(name = "LIDA_EM")
    private LocalDateTime lidaEm;

    protected Notificacao() {
    }

    public Notificacao(Long usuarioId, TipoNotificacao tipo, String titulo, String mensagem, Long demandaId,
                       String chave, LocalDateTime criadaEm) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.titulo = titulo;
        this.mensagem = mensagem;
        this.demandaId = demandaId;
        this.chave = chave;
        this.criadaEm = criadaEm;
    }

    public void marcarLida(LocalDateTime quando) {
        if (!lida) {
            lida = true;
            lidaEm = quando;
        }
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public TipoNotificacao getTipo() { return tipo; }
    public String getTitulo() { return titulo; }
    public String getMensagem() { return mensagem; }
    public Long getDemandaId() { return demandaId; }
    public String getChave() { return chave; }
    public boolean isLida() { return lida; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public LocalDateTime getLidaEm() { return lidaEm; }
}
