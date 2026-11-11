package br.com.nexusbuilderlab.semanexus.docia.documento;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Tipos aceitos no upload, com verificação pelos bytes iniciais (não só pela extensão). */
public enum TipoArquivo {
    PDF("pdf", "application/pdf"),
    TXT("txt", "text/plain"),
    MD("md", "text/markdown"),
    CSV("csv", "text/csv"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private static final int AMOSTRA_TEXTO = 8192;

    private final String extensao;
    private final String mime;

    TipoArquivo(String extensao, String mime) {
        this.extensao = extensao;
        this.mime = mime;
    }

    public String mime() {
        return mime;
    }

    /** Tipo correspondente à extensão do nome informado, se for uma das permitidas. */
    public static Optional<TipoArquivo> porNome(String nome) {
        if (nome == null) {
            return Optional.empty();
        }
        int ponto = nome.lastIndexOf('.');
        String ext = ponto < 0 ? "" : nome.substring(ponto + 1).trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.extensao.equals(ext)).findFirst();
    }

    /** PDF: começa com %PDF-; DOCX: zip (PK); texto: sem NUL, UTF-8 válido e sem cabeçalho executável. */
    public boolean conteudoValido(byte[] b) {
        return switch (this) {
            case PDF -> comeca(b, '%', 'P', 'D', 'F', '-');
            case DOCX -> comeca(b, 'P', 'K', 3, 4);
            case TXT, MD, CSV -> textoValido(b);
        };
    }

    private static boolean comeca(byte[] b, int... esperado) {
        if (b.length < esperado.length) {
            return false;
        }
        for (int i = 0; i < esperado.length; i++) {
            if ((b[i] & 0xFF) != esperado[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean textoValido(byte[] b) {
        if (comeca(b, 'M', 'Z') || comeca(b, 0x7F, 'E', 'L', 'F') || comeca(b, '#', '!')
                || comeca(b, 0xCA, 0xFE, 0xBA, 0xBE)) {
            return false;
        }
        int n = Math.min(b.length, AMOSTRA_TEXTO);
        for (int i = 0; i < n; i++) {
            if (b[i] == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
