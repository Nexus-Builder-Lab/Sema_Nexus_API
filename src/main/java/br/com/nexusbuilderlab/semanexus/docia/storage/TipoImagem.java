package br.com.nexusbuilderlab.semanexus.docia.storage;

import java.util.Optional;

/**
 * Imagens aceitas como evidência de fiscalização (Issue #145), reconhecidas pelos <em>magic bytes</em> do conteúdo —
 * nunca pela extensão nem pelo {@code Content-Type} enviados pelo cliente.
 */
public enum TipoImagem {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private static final int[] ASSINATURA_PNG = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final String mime;
    private final String extensao;

    TipoImagem(String mime, String extensao) {
        this.mime = mime;
        this.extensao = extensao;
    }

    public String mime() {
        return mime;
    }

    public String extensao() {
        return extensao;
    }

    /** Tipo reconhecido pelos primeiros bytes; vazio se não for JPEG, PNG nem WebP. */
    public static Optional<TipoImagem> detectar(byte[] b) {
        if (b == null) {
            return Optional.empty();
        }
        if (comeca(b, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (comeca(b, 0, ASSINATURA_PNG)) {
            return Optional.of(PNG);
        }
        if (b.length >= 12 && comeca(b, 0, 'R', 'I', 'F', 'F') && comeca(b, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean comeca(byte[] b, int deslocamento, int... esperado) {
        if (b.length < deslocamento + esperado.length) {
            return false;
        }
        for (int i = 0; i < esperado.length; i++) {
            if ((b[deslocamento + i] & 0xFF) != esperado[i]) {
                return false;
            }
        }
        return true;
    }
}
