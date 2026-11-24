package br.com.nexusbuilderlab.semanexus.docia.documento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TextExtractorTest {

    private final TextExtractor extrator = new TextExtractor("tesseract-inexistente-xyz", "por");

    private static byte[] docx() throws Exception {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            d.createParagraph().createRun().setText("Relatorio de vistoria em Sinop");
            XWPFTable t = d.createTable(2, 2);
            t.getRow(0).getCell(0).setText("Area");
            t.getRow(0).getCell(1).setText("Valor");
            t.getRow(1).getCell(0).setText("Embargada");
            t.getRow(1).getCell(1).setText("12 ha");
            d.write(o);
            return o.toByteArray();
        }
    }

    private static byte[] pdf(boolean comTexto) throws Exception {
        try (PDDocument d = new PDDocument(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            PDPage p = new PDPage();
            d.addPage(p);
            if (comTexto) {
                try (PDPageContentStream c = new PDPageContentStream(d, p)) {
                    c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    String[][] linhas = {{"Item", "Area"}, {"Alfa", "10"}, {"Beta", "20"}};
                    float y = 700;
                    for (String[] l : linhas) {
                        c.beginText(); c.newLineAtOffset(50, y); c.showText(l[0]); c.endText();
                        c.beginText(); c.newLineAtOffset(300, y); c.showText(l[1]); c.endText();
                        y -= 20;
                    }
                }
            }
            d.save(o);
            return o.toByteArray();
        }
    }

    @Test
    void docxExtraiTextoETabelaEmMarkdown() throws Exception {
        String t = extrator.extrair(TipoArquivo.DOCX, docx());
        assertThat(t).contains("Relatorio de vistoria em Sinop").contains("| Area | Valor |")
                .contains("| --- | --- |").contains("| Embargada | 12 ha |");
    }

    @Test
    void pdfExtraiTabelaPorPosicao() throws Exception {
        String t = extrator.extrair(TipoArquivo.PDF, pdf(true));
        assertThat(t).contains("Tabelas detectadas").contains("| Item | Area |").contains("| Beta | 20 |");
    }

    @Test
    void pdfSemTextoSemTesseractNaoFalhaEAvisa() throws Exception {
        TextExtractor.Extracao e = extrator.extrairCompleto(TipoArquivo.PDF, pdf(false));
        assertThat(e.texto()).isNull();
        assertThat(extrator.ocrDisponivel()).isFalse();
        assertThat(e.avisos()).singleElement().asString().contains("OCR indisponível");
    }

    @Test
    void preAnaliseSugereMetadados() {
        Municipio sinop = Mockito.mock(Municipio.class);
        Mockito.when(sinop.getId()).thenReturn(5107909);
        Mockito.when(sinop.getNome()).thenReturn("Sinop");
        String texto = "Laudo de vistoria\nEm 05 de março de 2026 a equipe esteve em Sinop. Desmatamento ilegal, "
                + "desmatamento recorrente. Ver DEM-2026/0010 e DEM-2026/0010 e DEM-2025/0003.";
        var r = PreAnaliseRegras.analisar("laudo.docx", texto, List.of(), List.of(sinop));
        assertThat(r.titulo()).isEqualTo("Laudo de vistoria");
        assertThat(r.municipioId()).isEqualTo(5107909);
        assertThat(r.dataDocumento()).isEqualTo(LocalDate.of(2026, 3, 5));
        assertThat(r.vinculosDemanda()).containsExactly("DEM-2026/0010", "DEM-2025/0003");
        assertThat(r.palavrasChave()).contains("desmatamento");
        assertThat(PreAnaliseRegras.analisar("a_b.pdf", null, List.of(), List.of()).titulo()).isEqualTo("a b");
    }
}
