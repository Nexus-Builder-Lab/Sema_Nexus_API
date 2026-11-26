package br.com.nexusbuilderlab.semanexus.core.auditoria;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Regras puras da cadeia SHA-256 da auditoria (Issue #148), sem banco: forma canônica do registro, cálculo do hash e
 * verificação sequencial.
 *
 * <p>Forma canônica (versão {@value #VERSAO}): cada campo vira {@code <tamanho>:<valor>;} (nulo = {@code N;}), na
 * ordem sequência, hash anterior, id, data (microssegundos), usuário, ação, entidade, id da entidade, detalhe, valor
 * anterior e valor novo. O prefixo de tamanho torna a serialização inequívoca (nenhum conteúdo consegue imitar a
 * fronteira entre campos). O primeiro registro encadeia com {@link #GENESE}.
 */
public final class CadeiaAuditoria {

    public static final String VERSAO = "SEMA-AUDITORIA-v1";
    public static final String ALGORITMO = "SHA-256";
    /** "Hash anterior" do primeiro registro da cadeia. */
    public static final String GENESE = "0".repeat(64);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS");

    private CadeiaAuditoria() {
    }

    /** Conteúdo de um registro de auditoria que entra no hash (como lido do banco). */
    public record Registro(long id, LocalDateTime data, Long usuarioId, String acao, String entidade,
                           String entidadeId, String detalhe, String valorAnterior, String valorNovo) {
    }

    /** Registro já selado: conteúdo + posição e hashes gravados. */
    public record Selado(Registro registro, long seq, String hashAnterior, String hashRegistro) {
    }

    public static String hash(long seq, String hashAnterior, Registro r) {
        StringBuilder sb = new StringBuilder(256).append(VERSAO).append('\n');
        campo(sb, Long.toString(seq));
        campo(sb, hashAnterior);
        campo(sb, Long.toString(r.id()));
        campo(sb, r.data() == null ? null : DATA.format(r.data()));
        campo(sb, r.usuarioId() == null ? null : r.usuarioId().toString());
        campo(sb, r.acao());
        campo(sb, r.entidade());
        campo(sb, r.entidadeId());
        campo(sb, r.detalhe());
        campo(sb, r.valorAnterior());
        campo(sb, r.valorNovo());
        try {
            byte[] d = MessageDigest.getInstance(ALGORITMO).digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }

    private static void campo(StringBuilder sb, String valor) {
        if (valor == null) {
            sb.append("N;");
        } else {
            sb.append(valor.length()).append(':').append(valor).append(';');
        }
    }

    /** Motivos de falha da verificação. */
    public enum Motivo {
        /** O conteúdo do registro não corresponde ao hash gravado (registro alterado). */
        HASH_DIVERGENTE,
        /** O "hash anterior" gravado não é o hash do registro anterior (registro removido, inserido ou reordenado). */
        ENCADEAMENTO_QUEBRADO,
        /** Falta uma posição da sequência (registro removido ou selagem apagada). */
        SEQUENCIA_AUSENTE,
        /** O fim da cadeia não confere com o estado registrado (registros finais removidos ou alterados). */
        FIM_DIVERGENTE
    }

    /** Primeira falha encontrada. */
    public record Falha(Long seq, Long auditoriaId, Motivo motivo) {
    }

    /**
     * Verificação sequencial: alimente os registros selados em ordem crescente de sequência ({@link #aceitar}) e
     * feche com o estado esperado do fim da cadeia ({@link #concluir}). Para na primeira falha.
     */
    public static final class Verificador {

        private long esperadoSeq = 1;
        private String hashAnterior = GENESE;
        private long verificados;
        private Falha falha;

        public boolean aceitar(Selado s) {
            if (falha != null) {
                return false;
            }
            Registro r = s.registro();
            if (s.seq() != esperadoSeq) {
                falha = new Falha(esperadoSeq, r.id(), Motivo.SEQUENCIA_AUSENTE);
                return false;
            }
            if (!hashAnterior.equals(s.hashAnterior())) {
                falha = new Falha(s.seq(), r.id(), Motivo.ENCADEAMENTO_QUEBRADO);
                return false;
            }
            if (!hash(s.seq(), s.hashAnterior(), r).equals(s.hashRegistro())) {
                falha = new Falha(s.seq(), r.id(), Motivo.HASH_DIVERGENTE);
                return false;
            }
            hashAnterior = s.hashRegistro();
            esperadoSeq++;
            verificados++;
            return true;
        }

        /** Confere o fim: a última sequência e o último hash devem ser os do estado da cadeia. */
        public void concluir(long ultimaSeq, String ultimoHash) {
            if (falha != null) {
                return;
            }
            long ultimaVista = esperadoSeq - 1;
            String esperadoHash = ultimaSeq == 0 ? null : ultimoHash;
            String vistoHash = ultimaVista == 0 ? null : hashAnterior;
            if (ultimaVista != ultimaSeq || !java.util.Objects.equals(esperadoHash, vistoHash)) {
                falha = new Falha(ultimaVista < ultimaSeq ? ultimaVista + 1 : ultimaSeq, null, Motivo.FIM_DIVERGENTE);
            }
        }

        public Falha falha() {
            return falha;
        }

        public long verificados() {
            return verificados;
        }

        public String ultimoHash() {
            return verificados == 0 ? null : hashAnterior;
        }
    }
}
