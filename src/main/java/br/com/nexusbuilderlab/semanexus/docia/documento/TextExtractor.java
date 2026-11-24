package br.com.nexusbuilderlab.semanexus.docia.documento;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extrai texto para pesquisa: PDFBox para PDF (tabelas detectadas por posição e OCR opcional via Tesseract),
 * Apache POI para DOCX (parágrafos e tabelas em Markdown) e leitura direta (UTF-8) para TXT/MD/CSV.
 * O OCR nunca derruba o upload: sem o binário, devolve aviso. Binário e idioma por
 * {@code TESSERACT_CMD} (padrão {@code tesseract}) e {@code TESSERACT_LANG} (padrão {@code por}).
 */
@Component
public class TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(TextExtractor.class);
    private static final long OCR_MAX_PIXELS = 25_000_000L;
    private static final int PDF_MAX_PAGINAS_TEXTO = 300;
    private static final long REDETECTAR_APOS_MS = 30_000;

    static {
        // Protecao contra zip bomb em DOCX (limites globais do POI, definidos uma vez).
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(50L * 1024 * 1024);
        ZipSecureFile.setMaxTextSize(20L * 1024 * 1024);
        IOUtils.setByteArrayMaxOverride(120_000_000);
    }

    private final Semaphore ocrVagas = new Semaphore(Math.max(1, Integer.parseInt(env("OCR_MAX_CONCORRENTE", "1"))));
    private volatile long ultimaFalhaDeteccao;
    private static final int OCR_MAX_PAGINAS = 10;
    private static final int OCR_DPI = 200;
    private static final long OCR_TIMEOUT_S = 60;
    private static final float LACUNA_COLUNA = 12f; // pontos entre palavras que separam colunas
    private static final char SEP = '\u0001';

    /** Texto extraído (ou {@code null}) e avisos legíveis (ex.: OCR indisponível). */
    public record Extracao(String texto, List<String> avisos) {
    }

    private final String tesseractCmd;
    private final String tesseractLang;
    private volatile Boolean tesseractDisponivel;

    public TextExtractor() {
        this(env("TESSERACT_CMD", "tesseract"), env("TESSERACT_LANG", "por"));
    }

    TextExtractor(String tesseractCmd, String tesseractLang) {
        this.tesseractCmd = tesseractCmd;
        this.tesseractLang = tesseractLang;
    }

    private static String env(String nome, String padrao) {
        String v = System.getenv(nome);
        return v == null || v.isBlank() ? padrao : v.strip();
    }

    /** Texto extraído, ou {@code null} quando o tipo não tem extração ou o arquivo não pôde ser lido. */
    public String extrair(TipoArquivo tipo, byte[] conteudo) {
        return extrairCompleto(tipo, conteudo).texto();
    }

    public Extracao extrairCompleto(TipoArquivo tipo, byte[] conteudo) {
        List<String> avisos = new ArrayList<>();
        String texto = switch (tipo) {
            case PDF -> extrairPdf(conteudo, avisos);
            case TXT, MD, CSV -> limpar(new String(conteudo, StandardCharsets.UTF_8));
            case DOCX -> extrairDocx(conteudo, avisos);
        };
        return new Extracao(texto, List.copyOf(avisos));
    }

    // ------------------------------------------------------------------ DOCX

    private String extrairDocx(byte[] conteudo, List<String> avisos) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(conteudo))) {
            StringBuilder sb = new StringBuilder();
            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph p) {
                    String t = p.getText();
                    if (t != null && !t.isBlank()) {
                        sb.append(t.strip()).append("\n\n");
                    }
                } else if (el instanceof XWPFTable tabela) {
                    List<List<String>> linhas = new ArrayList<>();
                    for (XWPFTableRow r : tabela.getRows()) {
                        List<String> cels = new ArrayList<>();
                        for (XWPFTableCell c : r.getTableCells()) {
                            cels.add(c.getText());
                        }
                        linhas.add(cels);
                    }
                    sb.append(paraMarkdown(linhas)).append('\n');
                }
            }
            return limpar(sb.toString());
        } catch (IOException | RuntimeException e) {
            log.warn("DOCX ilegível: {}", e.toString());
            avisos.add("Não foi possível ler o DOCX para extração de texto.");
            return null;
        }
    }

    /** Tabela em Markdown (primeira linha como cabeçalho). */
    static String paraMarkdown(List<List<String>> linhas) {
        int colunas = linhas.stream().mapToInt(List::size).max().orElse(0);
        if (linhas.isEmpty() || colunas == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < linhas.size(); i++) {
            sb.append(linha(linhas.get(i), colunas));
            if (i == 0) {
                sb.append('|').append(" --- |".repeat(colunas)).append('\n');
            }
        }
        return sb.toString();
    }

    private static String linha(List<String> cels, int colunas) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < colunas; i++) {
            String c = i < cels.size() ? cels.get(i) : "";
            sb.append(' ').append(c.replace('|', '/').replaceAll("\\s+", " ").strip()).append(" |");
        }
        return sb.append('\n').toString();
    }

    // ------------------------------------------------------------------ PDF

    private String extrairPdf(byte[] conteudo, List<String> avisos) {
        try (PDDocument doc = Loader.loadPDF(conteudo)) {
            if (doc.isEncrypted()) {
                return null;
            }
            ColunasStripper stripper = new ColunasStripper();
            stripper.setEndPage(PDF_MAX_PAGINAS_TEXTO);
            String texto = limpar(stripper.getText(doc).replace(String.valueOf(SEP), " "));
            if (texto == null) {
                return ocr(doc, avisos);
            }
            String tabelas = stripper.tabelasMarkdown();
            return tabelas.isEmpty() ? texto : texto + "\n\nTabelas detectadas:\n\n" + tabelas;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Marca lacunas largas entre palavras com {@code SEP} e acumula linhas consecutivas com 2+ colunas como tabelas. */
    private static final class ColunasStripper extends PDFTextStripper {
        private final List<List<List<String>>> tabelas = new ArrayList<>();
        private List<List<String>> atual = new ArrayList<>();

        ColunasStripper() {
            setSortByPosition(true);
        }

        private List<String> celulas = new ArrayList<>();
        private float ultimoY = Float.NaN;
        private float ultimoFimX;

        /** Chamado por palavra: agrupa palavras da mesma linha em células separadas por lacunas largas. */
        @Override
        protected void writeString(String texto, List<TextPosition> posicoes) throws IOException {
            if (!posicoes.isEmpty()) {
                TextPosition primeira = posicoes.get(0);
                TextPosition ultima = posicoes.get(posicoes.size() - 1);
                if (Float.isNaN(ultimoY) || Math.abs(primeira.getYDirAdj() - ultimoY) > 2f) {
                    fecharLinha();
                    celulas.add(texto);
                } else if (primeira.getXDirAdj() - ultimoFimX > LACUNA_COLUNA) {
                    celulas.add(texto);
                } else {
                    int k = celulas.size() - 1;
                    celulas.set(k, celulas.get(k) + " " + texto);
                }
                ultimoY = primeira.getYDirAdj();
                ultimoFimX = ultima.getXDirAdj() + ultima.getWidthDirAdj();
            }
            super.writeString(texto, posicoes);
        }

        private void fecharLinha() {
            if (celulas.size() >= 2) {
                atual.add(celulas);
            } else if (!celulas.isEmpty()) {
                fecharTabela();
            }
            celulas = new ArrayList<>();
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            fecharLinha();
            fecharTabela();
            ultimoY = Float.NaN;
            super.endPage(page);
        }

        private void fecharTabela() {
            if (atual.size() >= 2) {
                tabelas.add(atual);
            }
            atual = new ArrayList<>();
        }

        String tabelasMarkdown() {
            fecharLinha();
            fecharTabela();
            StringBuilder sb = new StringBuilder();
            for (List<List<String>> t : tabelas) {
                sb.append(paraMarkdown(t)).append('\n');
            }
            return sb.toString().strip();
        }
    }

    // ------------------------------------------------------------------ OCR (opcional)

    /** {@code true} se o binário do Tesseract responde a {@code --version}. Só o sucesso é guardado; falha é reavaliada após 30 s. */
    public boolean ocrDisponivel() {
        if (Boolean.TRUE.equals(tesseractDisponivel)) {
            return true;
        }
        long agora = System.currentTimeMillis();
        if (ultimaFalhaDeteccao != 0 && agora - ultimaFalhaDeteccao < REDETECTAR_APOS_MS) {
            return false;
        }
        boolean ok = executar(List.of(tesseractCmd, "--version"), 10) != null;
        if (ok) {
            tesseractDisponivel = true;
        } else {
            ultimaFalhaDeteccao = agora;
        }
        return ok;
    }

    private String ocr(PDDocument doc, List<String> avisos) {
        if (!ocrDisponivel()) {
            avisos.add("PDF sem camada de texto e OCR indisponível (Tesseract não encontrado; configure TESSERACT_CMD). "
                    + "Preencha os metadados manualmente.");
            return null;
        }
        boolean vaga = false;
        try {
            vaga = ocrVagas.tryAcquire(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!vaga) {
            avisos.add("OCR ocupado, texto extraído sem OCR. Tente novamente em instantes.");
            return null;
        }
        StringBuilder sb = new StringBuilder();
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("ocr-");
            PDFRenderer renderer = new PDFRenderer(doc);
            int paginas = Math.min(doc.getNumberOfPages(), OCR_MAX_PAGINAS);
            int puladas = 0;
            for (int i = 0; i < paginas; i++) {
                PDRectangle caixa = doc.getPage(i).getMediaBox();
                double pixels = (double) caixa.getWidth() / 72 * OCR_DPI * caixa.getHeight() / 72 * OCR_DPI;
                if (pixels > OCR_MAX_PIXELS) {
                    puladas++;
                    continue;
                }
                BufferedImage img = renderer.renderImageWithDPI(i, OCR_DPI);
                File png = tmp.resolve("p" + i + ".png").toFile();
                ImageIO.write(img, "png", png);
                String t = executar(List.of(tesseractCmd, png.getAbsolutePath(), "stdout", "-l", tesseractLang),
                        OCR_TIMEOUT_S);
                if (t != null) {
                    sb.append(t).append('\n');
                }
            }
            if (puladas > 0) {
                avisos.add("%d página(s) grande(s) demais foram ignoradas pelo OCR.".formatted(puladas));
            }
            if (doc.getNumberOfPages() > paginas) {
                avisos.add("OCR aplicado apenas às primeiras %d páginas.".formatted(OCR_MAX_PAGINAS));
            }
        } catch (IOException | RuntimeException e) {
            log.warn("OCR falhou: {}", e.getClass().getSimpleName());
            avisos.add("O OCR falhou; o documento foi enviado sem texto extraído.");
        } finally {
            ocrVagas.release();
            apagar(tmp);
        }
        String texto = limpar(sb.toString());
        if (texto != null) {
            avisos.add("Texto obtido por OCR (Tesseract): pode conter erros de reconhecimento.");
        } else if (avisos.isEmpty()) {
            avisos.add("OCR executado, mas nenhum texto foi reconhecido.");
        }
        return texto;
    }

    /** Executa o comando com timeout real: a saída vai para arquivo (leitura nunca bloqueia) e o processo é encerrado no fim. */
    static String executar(List<String> comando, long timeoutSegundos) {
        Process p = null;
        Path saida = null;
        try {
            saida = Files.createTempFile("ocr-out-", ".txt");
            p = new ProcessBuilder(comando).redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(saida.toFile()).start();
            if (!p.waitFor(timeoutSegundos, TimeUnit.SECONDS)) {
                return null;
            }
            return p.exitValue() == 0 ? Files.readString(saida, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
                try {
                    p.waitFor(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (saida != null) {
                saida.toFile().delete();
            }
        }
    }

    private static void apagar(Path dir) {
        if (dir == null) {
            return;
        }
        try (var s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
        } catch (IOException ignorado) {
            // limpeza best-effort
        }
    }

    private static String limpar(String texto) {
        String t = texto.replace("\u0000", "").strip();
        return t.isEmpty() ? null : t;
    }
}
