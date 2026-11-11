package br.com.nexusbuilderlab.semanexus.docia.documento;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Extrai texto para pesquisa: PDFBox para PDF; leitura direta (UTF-8) para TXT/MD/CSV; DOCX sem extração no MVP. */
@Component
public class TextExtractor {

    /** Texto extraído, ou {@code null} quando o tipo não tem extração ou o PDF não pôde ser lido. */
    public String extrair(TipoArquivo tipo, byte[] conteudo) {
        return switch (tipo) {
            case PDF -> extrairPdf(conteudo);
            case TXT, MD, CSV -> limpar(new String(conteudo, StandardCharsets.UTF_8));
            case DOCX -> null;
        };
    }

    private String extrairPdf(byte[] conteudo) {
        try (PDDocument doc = Loader.loadPDF(conteudo)) {
            if (doc.isEncrypted()) {
                return null;
            }
            return limpar(new PDFTextStripper().getText(doc));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String limpar(String texto) {
        String t = texto.replace("\u0000", "").strip();
        return t.isEmpty() ? null : t;
    }
}
