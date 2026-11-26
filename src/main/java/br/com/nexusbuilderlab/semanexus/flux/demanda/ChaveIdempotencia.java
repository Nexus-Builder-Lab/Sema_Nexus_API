package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Registro de idempotência para sincronização do modo campo (Issue #147, tabela {@code CHAVE_IDEMPOTENCIA}).
 * Escopo por {@code (usuarioId, operacao, demandaId, chave)} com hash SHA-256 do payload canônico e snapshot JSON da
 * resposta original.
 */
@Entity
@Table(name = "CHAVE_IDEMPOTENCIA")
public class ChaveIdempotencia {

    public static final String CABECALHO = "Idempotency-Key";
    public static final String CABECALHO_REPLAY = "Idempotency-Replayed";
    public static final int TAMANHO_MAXIMO = 128;
    public static final String PADRAO_CHAVE = "^[A-Za-z0-9._:-]{1,128}$";
    private static final Pattern REGEX_CHAVE = Pattern.compile(PADRAO_CHAVE);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "USUARIO_ID", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "DEMANDA_ID", nullable = false, updatable = false)
    private Long demandaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "OPERACAO", nullable = false, length = 30, updatable = false)
    private OperacaoIdempotente operacao;

    @Column(name = "CHAVE", nullable = false, length = 128, updatable = false)
    private String chave;

    @Column(name = "HASH_REQUISICAO", nullable = false, length = 64, updatable = false)
    private String hashRequisicao;

    @Column(name = "STATUS_HTTP", nullable = false, updatable = false)
    private int statusHttp;

    @Lob
    @Column(name = "CORPO_RESPOSTA", nullable = false, updatable = false)
    private String corpoResposta;

    @Column(name = "CONCLUIDA", nullable = false, updatable = false)
    private boolean concluida = true;

    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "EXPIRA_EM", nullable = false, updatable = false)
    private LocalDateTime expiraEm;

    protected ChaveIdempotencia() {
    }

    public ChaveIdempotencia(Long usuarioId, Long demandaId, OperacaoIdempotente operacao, String chave,
                             String hashRequisicao, int statusHttp, String corpoResposta, LocalDateTime criadoEm,
                             LocalDateTime expiraEm) {
        this.usuarioId = usuarioId;
        this.demandaId = demandaId;
        this.operacao = operacao;
        this.chave = chave;
        this.hashRequisicao = hashRequisicao;
        this.statusHttp = statusHttp;
        this.corpoResposta = corpoResposta;
        this.concluida = true;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    /** Valida formato e tamanho do cabeçalho {@code Idempotency-Key} quando informado (1..128 chars `[A-Za-z0-9._:-]`). */
    public static String validarChave(String chave) {
        if (chave == null || chave.isBlank()) {
            throw new DadosInvalidosException(CABECALHO, "O cabeçalho Idempotency-Key não pode ser vazio.");
        }
        if (chave.length() > TAMANHO_MAXIMO) {
            throw new DadosInvalidosException(CABECALHO,
                    "O cabeçalho Idempotency-Key deve ter no máximo " + TAMANHO_MAXIMO + " caracteres.");
        }
        if (!REGEX_CHAVE.matcher(chave).matches()) {
            throw new DadosInvalidosException(CABECALHO,
                    "O cabeçalho Idempotency-Key aceita apenas letras ASCII, dígitos e '.', '_', ':', '-'.");
        }
        return chave;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getDemandaId() {
        return demandaId;
    }

    public OperacaoIdempotente getOperacao() {
        return operacao;
    }

    public String getChave() {
        return chave;
    }

    public String getHashRequisicao() {
        return hashRequisicao;
    }

    public int getStatusHttp() {
        return statusHttp;
    }

    public String getCorpoResposta() {
        return corpoResposta;
    }

    public boolean isConcluida() {
        return concluida;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getExpiraEm() {
        return expiraEm;
    }
}
