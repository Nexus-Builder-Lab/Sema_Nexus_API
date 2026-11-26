package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.geo.Geodesia;
import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.AuditoriaDto;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoService.DocumentoVinculado;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ChecklistModelo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ChecklistModelo.Item;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ChecklistPreenchido;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FiscalizacaoDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FotoFiscalizacao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.HistoricoItem;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteLoader;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Gerador vetorial do dossiê da demanda em PDF (PDFBox 3 + ZXing, Issue #146).
 * Produz documento A4 paginado com faixa demonstrativa em todas as páginas, dados cadastrais,
 * croqui vetorial em Mato Grosso, linha do tempo, fiscalização/checklist/fotos, documentos com SHA-256
 * e bloco de verificação de auditoria com QR Code.
 */
final class DossiePdfGerador {

    private static final float MARGEM_ESQ = 40f;
    private static final float MARGEM_DIR = 40f;
    private static final float LARGURA_CONTEUDO = PDRectangle.A4.getWidth() - MARGEM_ESQ - MARGEM_DIR;
    private static final float Y_INICIO = PDRectangle.A4.getHeight() - 52f;
    private static final float Y_LIMITE = 46f;

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA_HORA_SEG = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    // Paleta institucional sobria (contraste alto para impressão e leitura em tela)
    private static final Color COR_PRIMARIA = new Color(15, 118, 110);     // teal-700
    private static final Color COR_ESCURA = new Color(15, 23, 42);         // slate-900
    private static final Color COR_TEXTO = new Color(30, 41, 59);          // slate-800
    private static final Color COR_SUAVE = new Color(100, 116, 139);       // slate-500
    private static final Color COR_BORDA = new Color(203, 213, 225);       // slate-300
    private static final Color COR_FUNDO_SUAVE = new Color(248, 250, 252); // slate-50
    private static final Color COR_FAIXA_FUNDO = new Color(254, 243, 199); // amber-100
    private static final Color COR_FAIXA_BORDA = new Color(180, 83, 9);    // amber-700
    private static final Color COR_FAIXA_TEXTO = new Color(120, 53, 15);   // amber-900
    private static final Color COR_CROQUI_FILL = new Color(209, 250, 229); // emerald-100
    private static final Color COR_ALERTA = new Color(185, 28, 28);        // red-700

    private static final double MT_LON_MIN = ReferenciaSateliteLoader.LON_MIN.doubleValue();
    private static final double MT_LON_MAX = ReferenciaSateliteLoader.LON_MAX.doubleValue();
    private static final double MT_LAT_MIN = ReferenciaSateliteLoader.LAT_MIN.doubleValue();
    private static final double MT_LAT_MAX = ReferenciaSateliteLoader.LAT_MAX.doubleValue();

    /** Contorno esquemático simplificado do território de Mato Grosso (lon, lat em WGS84). */
    private static final double[][] CONTORNO_MT = {
            {-61.5, -8.8}, {-59.5, -7.4}, {-57.8, -7.4}, {-54.8, -9.5}, {-50.6, -9.8},
            {-50.2, -13.0}, {-51.8, -15.8}, {-53.2, -17.9}, {-55.5, -17.9}, {-57.7, -17.4},
            {-58.3, -16.1}, {-60.2, -15.1}, {-60.4, -13.2}, {-61.6, -11.5}, {-61.5, -8.8}
    };

    private final PDDocument doc;
    private final PDFont fRegular = new PDType1Font(FontName.HELVETICA);
    private final PDFont fNegrito = new PDType1Font(FontName.HELVETICA_BOLD);
    private final PDFont fMono = new PDType1Font(FontName.COURIER);
    private final PDFont fMonoNegrito = new PDType1Font(FontName.COURIER_BOLD);

    private PDPage paginaAtual;
    private PDPageContentStream cs;
    private float y;

    private DossiePdfGerador(PDDocument doc) {
        this.doc = doc;
    }

    static byte[] gerar(DemandaDetalhe demanda, List<HistoricoItem> historico, List<DocumentoVinculado> docs,
                        List<FotoFiscalizacao> fotos, List<AuditoriaDto> auditorias, UsuarioAutenticado ator,
                        LocalDateTime emitidoEm, String codigoVerificacao, String payloadQr) {
        try (PDDocument doc = new PDDocument()) {
            doc.getDocumentInformation().setTitle("Dossiê " + demanda.numero() + " — SEMA ALERTA (Demonstrativo)");
            doc.getDocumentInformation().setAuthor("SEMA ALERTA");
            doc.getDocumentInformation().setSubject(DossieDemandaService.FAIXA_DEMONSTRATIVA);

            DossiePdfGerador g = new DossiePdfGerador(doc);
            g.novaPagina();
            g.desenharCabecalhoPrincipal(demanda, ator, emitidoEm);
            g.desenharDadosCadastrais(demanda);
            g.desenharCroquiVetorial(demanda);
            g.desenharLinhaDoTempo(historico);
            g.desenharFiscalizacao(demanda, fotos);
            g.desenharDocumentos(docs);
            g.desenharBlocoAuditoriaEqr(demanda, auditorias, ator, emitidoEm, codigoVerificacao, payloadQr);
            g.fecharFluxoAtual();
            g.carimbarFaixaERodapeEmTodasAsPaginas(demanda.numero(), codigoVerificacao);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o PDF do dossiê.", e);
        }
    }

    // ------------------------------------------------------------------ seções

    private void desenharCabecalhoPrincipal(DemandaDetalhe d, UsuarioAutenticado ator, LocalDateTime emitidoEm)
            throws IOException {
        texto(fNegrito, 15, COR_ESCURA, MARGEM_ESQ, y, "DOSSIÊ DE DEMANDA DE FISCALIZAÇÃO");
        y -= 18;
        List<String> linhasTitulo = quebrar(fNegrito, 11.5f, d.numero() + " — " + valor(d.titulo()), LARGURA_CONTEUDO);
        for (String linha : linhasTitulo) {
            garantirEspaco(16);
            texto(fNegrito, 11.5f, COR_PRIMARIA, MARGEM_ESQ, y, linha);
            y -= 15;
        }
        y -= 2;
        String meta = "Situação: " + d.status().rotulo() + (d.atrasada() ? " (ATRASADA)" : "")
                + "   |   Origem: " + d.origem().rotulo()
                + "   |   Prioridade: " + (d.prioridade() == null ? "Não definida" : d.prioridade().rotulo());
        retanguloPreenchido(MARGEM_ESQ, y - 16, LARGURA_CONTEUDO, 22, COR_FUNDO_SUAVE, COR_BORDA);
        texto(fNegrito, 8.5f, COR_TEXTO, MARGEM_ESQ + 8, y - 8, meta);
        y -= 26;
        texto(fRegular, 8f, COR_SUAVE, MARGEM_ESQ, y,
                "Emitido em " + DATA_HORA_SEG.format(emitidoEm) + " (America/Cuiaba) por "
                        + ator.nome() + " (" + ator.perfil().name() + ")");
        y -= 16;
    }

    private void desenharDadosCadastrais(DemandaDetalhe d) throws IOException {
        tituloSecao("1. DADOS CADASTRAIS DA DEMANDA");
        String municipio = d.municipio().nome() + " (IBGE " + d.municipio().id() + " · " + d.municipio().bioma().rotulo() + ")";
        String coords = d.latitude() != null && d.longitude() != null
                ? formatarCoord(d.latitude()) + ", " + formatarCoord(d.longitude())
                : "Não informadas";
        String area = d.areaHa() != null ? formatarNumero(d.areaHa()) + " ha" : "Não informada";
        String equipe = d.equipe() != null ? d.equipe().nome() : "Não atribuída";
        String fiscal = d.fiscal() != null ? d.fiscal().nome() : "Não designado";
        String categoria = d.categoria() != null ? d.categoria().rotulo() : "Não classificada";

        linhaDupla("Número / ID", d.numero() + " (#" + d.id() + ")", "Categoria", categoria);
        linhaDupla("Município / Bioma", municipio, "Localidade", valorOu(d.localidade(), "Não informada"));
        linhaDupla("Coordenadas (WGS84)", coords, "Área afetada", area);
        linhaDupla("Data de recebimento", data(d.dataRecebimento()), "Prazo sugerido", dataOu(d.prazoSugerido(), "—"));
        linhaDupla("Prazo de execução", dataOu(d.prazo(), "Não definido"), "Concluída em", dataHoraOu(d.concluidaEm(), "—"));
        linhaDupla("Equipe responsável", equipe, "Fiscal designado", fiscal);
        linhaDupla("Cadastrada por", d.criadoPor().nome() + " em " + dataHoraOu(d.criadoEm(), "—"),
                "Última atualização", dataHoraOu(d.atualizadoEm(), "—"));

        blocoTexto("Relato / Descrição", d.descricao());
        if (d.observacoes() != null && !d.observacoes().isBlank()) {
            blocoTexto("Observações", d.observacoes());
        }
        if (d.resultadoFinal() != null && !d.resultadoFinal().isBlank()) {
            blocoTexto("Resultado final (" + dataHoraOu(d.concluidaEm(), "concluída") + ")",
                    d.resultadoFinal() + (d.justificativaConclusao() != null
                            ? "\nJustificativa: " + d.justificativaConclusao() : ""));
        }
        y -= 6;
    }

    private void desenharCroquiVetorial(DemandaDetalhe d) throws IOException {
        float alturaCaixa = 175f;
        garantirEspaco(alturaCaixa + 36f);
        tituloSecao("2. CROQUI VETORIAL DE LOCALIZAÇÃO EM MATO GROSSO (WGS84)");

        float gap = 14f;
        float largEsq = 240f;
        float largDir = LARGURA_CONTEUDO - largEsq - gap;
        float topoY = y;
        float baseY = topoY - alturaCaixa;

        // Painel A: Enquadramento em Mato Grosso
        desenharPainelMt(d, MARGEM_ESQ, baseY, largEsq, alturaCaixa);

        // Painel B: Detalhe do polígono ou coordenada pontual
        desenharPainelDetalheArea(d, MARGEM_ESQ + largEsq + gap, baseY, largDir, alturaCaixa);

        y = baseY - 14f;
    }

    private void desenharPainelMt(DemandaDetalhe d, float x, float yBase, float w, float h) throws IOException {
        retanguloPreenchido(x, yBase, w, h, Color.WHITE, COR_BORDA);
        texto(fNegrito, 8f, COR_ESCURA, x + 8, yBase + h - 13, "Enquadramento em Mato Grosso");

        float padEsq = 26f;
        float padDir = 10f;
        float padInf = 20f;
        float padSup = 22f;
        float mx = x + padEsq;
        float my = yBase + padInf;
        float mw = w - padEsq - padDir;
        float mh = h - padInf - padSup;

        // Grade geográfica sutil
        cs.setStrokingColor(new Color(226, 232, 240));
        cs.setLineWidth(0.5f);
        for (double lon : new double[] {-60, -55, -50}) {
            if (lon >= MT_LON_MIN && lon <= MT_LON_MAX) {
                float px = mapX(lon, MT_LON_MIN, MT_LON_MAX, mx, mw);
                linha(px, my, px, my + mh);
                texto(fRegular, 6.5f, COR_SUAVE, px - 8, my - 9, ((int) lon) + "°");
            }
        }
        for (double lat : new double[] {-16, -12, -8}) {
            if (lat >= MT_LAT_MIN && lat <= MT_LAT_MAX) {
                float py = mapY(lat, MT_LAT_MIN, MT_LAT_MAX, my, mh);
                linha(mx, py, mx + mw, py);
                texto(fRegular, 6.5f, COR_SUAVE, x + 4, py - 2, ((int) lat) + "°");
            }
        }
        cs.setStrokingColor(COR_BORDA);
        cs.setLineWidth(0.75f);
        cs.addRect(mx, my, mw, mh);
        cs.stroke();

        // Contorno esquemático do estado de MT
        cs.setNonStrokingColor(new Color(241, 245, 249));
        cs.setStrokingColor(new Color(100, 116, 139));
        cs.setLineWidth(1.0f);
        for (int i = 0; i < CONTORNO_MT.length; i++) {
            float px = mapX(CONTORNO_MT[i][0], MT_LON_MIN, MT_LON_MAX, mx, mw);
            float py = mapY(CONTORNO_MT[i][1], MT_LAT_MIN, MT_LAT_MAX, my, mh);
            if (i == 0) {
                cs.moveTo(px, py);
            } else {
                cs.lineTo(px, py);
            }
        }
        cs.closePath();
        cs.fillAndStroke();

        // Seta Norte
        float nx = mx + mw - 12;
        float ny = my + mh - 14;
        texto(fNegrito, 7f, COR_ESCURA, nx - 2.5f, ny + 4, "N");
        cs.setStrokingColor(COR_ESCURA);
        cs.setLineWidth(1.0f);
        linha(nx, ny - 6, nx, ny + 2);

        // Plota bounding/polígono da demanda e/ou marcador pontual
        List<List<List<double[]>>> poligonos = extrairPoligonos(d.areaGeometria());
        if (!poligonos.isEmpty()) {
            cs.setNonStrokingColor(COR_PRIMARIA);
            cs.setStrokingColor(COR_PRIMARIA);
            cs.setLineWidth(1.2f);
            for (List<List<double[]>> poly : poligonos) {
                if (!poly.isEmpty()) {
                    List<double[]> ext = poly.get(0);
                    for (int i = 0; i < ext.size(); i++) {
                        float px = mapX(ext.get(i)[0], MT_LON_MIN, MT_LON_MAX, mx, mw);
                        float py = mapY(ext.get(i)[1], MT_LAT_MIN, MT_LAT_MAX, my, mh);
                        if (i == 0) {
                            cs.moveTo(px, py);
                        } else {
                            cs.lineTo(px, py);
                        }
                    }
                    cs.closePath();
                    cs.fillAndStroke();
                }
            }
        }

        double[] pontoRef = pontoReferencia(d, poligonos);
        if (pontoRef != null) {
            float px = mapX(pontoRef[1], MT_LON_MIN, MT_LON_MAX, mx, mw);
            float py = mapY(pontoRef[0], MT_LAT_MIN, MT_LAT_MAX, my, mh);
            desenharAlvo(px, py, COR_ALERTA);
            texto(fRegular, 6.5f, COR_TEXTO, x + 8, yBase + 6,
                    "Município: " + d.municipio().nome() + " · Ref: "
                            + String.format(Locale.US, "%.4f, %.4f", pontoRef[0], pontoRef[1]));
        } else {
            texto(fRegular, 6.5f, COR_SUAVE, x + 8, yBase + 6,
                    "Município: " + d.municipio().nome() + " (sem coordenada pontual)");
        }
    }

    private void desenharPainelDetalheArea(DemandaDetalhe d, float x, float yBase, float w, float h) throws IOException {
        retanguloPreenchido(x, yBase, w, h, Color.WHITE, COR_BORDA);
        List<List<List<double[]>>> poligonos = extrairPoligonos(d.areaGeometria());
        if (!poligonos.isEmpty()) {
            texto(fNegrito, 8f, COR_ESCURA, x + 8, yBase + h - 13, "Croqui da Área Afetada (Polígono)");
            double minLon = Double.MAX_VALUE;
            double maxLon = -Double.MAX_VALUE;
            double minLat = Double.MAX_VALUE;
            double maxLat = -Double.MAX_VALUE;
            int totalVertices = 0;
            for (List<List<double[]>> poly : poligonos) {
                for (List<double[]> ring : poly) {
                    for (double[] pt : ring) {
                        minLon = Math.min(minLon, pt[0]);
                        maxLon = Math.max(maxLon, pt[0]);
                        minLat = Math.min(minLat, pt[1]);
                        maxLat = Math.max(maxLat, pt[1]);
                    }
                    totalVertices += Math.max(0, ring.size() - 1);
                }
            }
            double midLat = (minLat + maxLat) / 2.0;
            double cosLat = Math.max(0.2, Math.cos(Math.toRadians(midLat)));
            double spanLon = Math.max(1e-5, maxLon - minLon) * cosLat;
            double spanLat = Math.max(1e-5, maxLat - minLat);

            float pad = 16f;
            float gx = x + pad;
            float gy = yBase + 28f;
            float gw = w - 2 * pad;
            float gh = h - 50f;
            retanguloPreenchido(gx, gy, gw, gh, COR_FUNDO_SUAVE, new Color(226, 232, 240));

            double escala = Math.min((gw - 16f) / spanLon, (gh - 16f) / spanLat);
            double centroLon = (minLon + maxLon) / 2.0;
            double centroLat = (minLat + maxLat) / 2.0;
            float cx = gx + gw / 2f;
            float cy = gy + gh / 2f;

            for (List<List<double[]>> poly : poligonos) {
                if (poly.isEmpty()) {
                    continue;
                }
                // Anel exterior
                desenharAnel(poly.get(0), centroLon, centroLat, cosLat, escala, cx, cy,
                        COR_CROQUI_FILL, COR_PRIMARIA, 1.5f, false);
                // Furos (anéis interiores)
                for (int r = 1; r < poly.size(); r++) {
                    desenharAnel(poly.get(r), centroLon, centroLat, cosLat, escala, cx, cy,
                            COR_FUNDO_SUAVE, COR_SUAVE, 1.0f, true);
                }
                if (totalVertices <= 60) {
                    cs.setNonStrokingColor(COR_ESCURA);
                    for (double[] pt : poly.get(0)) {
                        float vx = (float) (cx + (pt[0] - centroLon) * cosLat * escala);
                        float vy = (float) (cy + (pt[1] - centroLat) * escala);
                        circulo(vx, vy, 1.6f, true);
                    }
                }
            }

            // Marca a coordenada informada / centroide
            double[] ponto = pontoReferencia(d, poligonos);
            if (ponto != null) {
                float vx = (float) (cx + (ponto[1] - centroLon) * cosLat * escala);
                float vy = (float) (cy + (ponto[0] - centroLat) * escala);
                if (vx >= gx && vx <= gx + gw && vy >= gy && vy <= gy + gh) {
                    desenharAlvo(vx, vy, COR_ALERTA);
                }
            }

            // Barra de escala proporcional (km/m)
            double larguraKm = Geodesia.haversineKm(midLat, minLon, midLat, maxLon);
            desenharBarraEscala(gx + 8, gy + 8, gw, (maxLon - minLon) * cosLat * escala, larguraKm);

            String info = "Área: " + (d.areaHa() != null ? formatarNumero(d.areaHa()) + " ha" : "—")
                    + " · Vértices: " + totalVertices
                    + (poligonos.size() > 1 ? " · " + poligonos.size() + " polígonos" : "");
            texto(fRegular, 7f, COR_TEXTO, x + 8, yBase + 10, info);
        } else if (d.latitude() != null && d.longitude() != null) {
            texto(fNegrito, 8f, COR_ESCURA, x + 8, yBase + h - 13, "Ponto Georreferenciado (Sem Polígono)");
            float gx = x + 16f;
            float gy = yBase + 28f;
            float gw = w - 32f;
            float gh = h - 50f;
            retanguloPreenchido(gx, gy, gw, gh, COR_FUNDO_SUAVE, new Color(226, 232, 240));
            float cx = gx + gw / 2f;
            float cy = gy + gh / 2f;
            cs.setStrokingColor(new Color(148, 163, 184));
            cs.setLineWidth(0.75f);
            cs.setLineDashPattern(new float[] {3f, 3f}, 0);
            circulo(cx, cy, 38f, false);
            circulo(cx, cy, 19f, false);
            cs.setLineDashPattern(new float[] {}, 0);
            linha(cx - 46f, cy, cx + 46f, cy);
            linha(cx, cy - 46f, cx, cy + 46f);
            desenharAlvo(cx, cy, COR_ALERTA);
            texto(fRegular, 7f, COR_TEXTO, x + 8, yBase + 10,
                    "Lat " + formatarCoord(d.latitude()) + ", Lon " + formatarCoord(d.longitude())
                            + " · Área informada: " + (d.areaHa() != null ? formatarNumero(d.areaHa()) + " ha" : "—"));
        } else {
            texto(fNegrito, 8f, COR_ESCURA, x + 8, yBase + h - 13, "Croqui da Área");
            texto(fRegular, 8.5f, COR_SUAVE, x + 14, yBase + h / 2f,
                    "Sem polígono nem coordenada geográfica");
            texto(fRegular, 8.5f, COR_SUAVE, x + 14, yBase + h / 2f - 13f,
                    "cadastrada para esta demanda.");
        }
    }

    private void desenharAnel(List<double[]> ring, double centroLon, double centroLat, double cosLat,
                              double escala, float cx, float cy, Color fill, Color stroke,
                              float largura, boolean tracejado) throws IOException {
        if (ring.isEmpty()) {
            return;
        }
        cs.setNonStrokingColor(fill);
        cs.setStrokingColor(stroke);
        cs.setLineWidth(largura);
        if (tracejado) {
            cs.setLineDashPattern(new float[] {3f, 2f}, 0);
        }
        for (int i = 0; i < ring.size(); i++) {
            float px = (float) (cx + (ring.get(i)[0] - centroLon) * cosLat * escala);
            float py = (float) (cy + (ring.get(i)[1] - centroLat) * escala);
            if (i == 0) {
                cs.moveTo(px, py);
            } else {
                cs.lineTo(px, py);
            }
        }
        cs.closePath();
        cs.fillAndStroke();
        if (tracejado) {
            cs.setLineDashPattern(new float[] {}, 0);
        }
    }

    private void desenharBarraEscala(float x, float yBarra, float larguraDisponivel,
                                     double larguraBboxPt, double larguraBboxKm) throws IOException {
        if (larguraBboxKm <= 0 || larguraBboxPt <= 0) {
            return;
        }
        double[] opcoesKm = {0.05, 0.1, 0.25, 0.5, 1, 2, 5, 10, 25, 50, 100};
        double escolhida = opcoesKm[0];
        for (double op : opcoesKm) {
            double px = (op / larguraBboxKm) * larguraBboxPt;
            if (px <= larguraDisponivel * 0.45 && px >= 24) {
                escolhida = op;
            }
        }
        float barraPt = (float) Math.min(larguraDisponivel * 0.5, Math.max(20.0, (escolhida / larguraBboxKm) * larguraBboxPt));
        cs.setStrokingColor(COR_ESCURA);
        cs.setLineWidth(1.2f);
        linha(x, yBarra, x + barraPt, yBarra);
        linha(x, yBarra - 2.5f, x, yBarra + 2.5f);
        linha(x + barraPt, yBarra - 2.5f, x + barraPt, yBarra + 2.5f);
        String rotulo = escolhida < 1.0
                ? ((int) Math.round(escolhida * 1000)) + " m"
                : (escolhida == Math.floor(escolhida) ? ((int) escolhida) + " km" : escolhida + " km");
        texto(fNegrito, 6.5f, COR_ESCURA, x + barraPt + 4, yBarra - 2f, rotulo);
    }

    private void desenharLinhaDoTempo(List<HistoricoItem> historico) throws IOException {
        tituloSecao("3. LINHA DO TEMPO (ORDEM CRONOLÓGICA)");
        if (historico.isEmpty()) {
            textoSimples("Nenhum evento registrado no histórico.");
            y -= 6;
            return;
        }
        List<HistoricoItem> cronologico = new ArrayList<>(historico);
        Collections.reverse(cronologico);

        for (HistoricoItem h : cronologico) {
            String transicao = h.statusAnterior() != null && h.statusNovo() != null
                    && h.statusAnterior() != h.statusNovo()
                    ? " (" + h.statusAnterior().rotulo() + " -> " + h.statusNovo().rotulo() + ")"
                    : h.statusNovo() != null ? " (" + h.statusNovo().rotulo() + ")" : "";
            String cab = DATA_HORA.format(h.data()) + "  |  " + h.tipo().name() + transicao
                    + "  —  " + (h.usuario() != null ? h.usuario().nome() : "Sistema");
            List<String> linhasTexto = h.texto() == null || h.texto().isBlank()
                    ? List.of()
                    : quebrar(fRegular, 8.5f, h.texto(), LARGURA_CONTEUDO - 18f);
            float necessidade = 14f + linhasTexto.size() * 11f + 4f;
            garantirEspaco(Math.min(necessidade, 54f));

            // Marcador da linha do tempo
            cs.setNonStrokingColor(COR_PRIMARIA);
            circulo(MARGEM_ESQ + 4, y + 3, 2.3f, true);
            texto(fNegrito, 8.5f, COR_ESCURA, MARGEM_ESQ + 12, y, cab);
            y -= 12f;
            for (String l : linhasTexto) {
                garantirEspaco(13f);
                texto(fRegular, 8.5f, COR_TEXTO, MARGEM_ESQ + 12, y, l);
                y -= 11f;
            }
            y -= 3f;
        }
        y -= 4f;
    }

    private void desenharFiscalizacao(DemandaDetalhe d, List<FotoFiscalizacao> fotos) throws IOException {
        tituloSecao("4. FISCALIZAÇÃO, CHECKLIST E EVIDÊNCIAS FOTOGRÁFICAS");
        FiscalizacaoDetalhe f = d.fiscalizacao();
        if (f == null) {
            textoSimples("Registro de fiscalização ainda não preenchido para esta demanda.");
        } else {
            linhaDupla("Data da vistoria", data(f.data()),
                    "Equipe", f.equipe() != null ? f.equipe().nome() : "—");
            linhaDupla("Registrada por", f.registradoPor() != null ? f.registradoPor().nome() : "—",
                    "Registrada em", dataHoraOu(f.registradoEm(), "—"));
            blocoTexto("Atividades realizadas", f.atividades());
            blocoTexto("Resultado da fiscalização", f.resultado());
            blocoTexto("Evidências descritas", f.evidencias());
            if (f.recomendacoes() != null && !f.recomendacoes().isBlank()) {
                blocoTexto("Recomendações", f.recomendacoes());
            }
            desenharChecklist(f.checklist());
        }

        if (!fotos.isEmpty()) {
            garantirEspaco(36f);
            texto(fNegrito, 9f, COR_ESCURA, MARGEM_ESQ, y,
                    "Fotos de evidência anexadas (" + fotos.size() + ") — EXIF informativo, sem valor de prova:");
            y -= 14f;
            int idx = 1;
            for (FotoFiscalizacao foto : fotos) {
                garantirEspaco(42f);
                String cab = String.format(PT_BR, "%d. %s (%s, %s) — Conferência EXIF: %s%s",
                        idx++, foto.nomeOriginal(), foto.mime(), formatarBytes(foto.tamanho()),
                        foto.conferencia().name(),
                        foto.distanciaKm() != null ? " (" + formatarNumero(foto.distanciaKm()) + " km)" : "");
                retanguloPreenchido(MARGEM_ESQ, y - 24, LARGURA_CONTEUDO, 32, COR_FUNDO_SUAVE, COR_BORDA);
                texto(fNegrito, 8f, COR_TEXTO, MARGEM_ESQ + 6, y - 2, cab);
                texto(fMono, 7.5f, COR_ESCURA, MARGEM_ESQ + 6, y - 12, "SHA-256: " + foto.sha256());
                String exifInfo = "EXIF data/hora: " + dataHoraOu(foto.exifDataHora(), "ausente")
                        + "  |  GPS: " + (foto.exifLatitude() != null && foto.exifLongitude() != null
                        ? formatarCoord(foto.exifLatitude()) + ", " + formatarCoord(foto.exifLongitude())
                        + (foto.gpsForaMt() ? " (FORA DE MT)" : "")
                        : "ausente");
                texto(fRegular, 7.5f, COR_SUAVE, MARGEM_ESQ + 6, y - 21, exifInfo);
                y -= 37f;
            }
        }
        y -= 4f;
    }

    private void desenharChecklist(ChecklistPreenchido checklist) throws IOException {
        if (checklist == null || checklist.itens() == null || checklist.itens().isEmpty()) {
            return;
        }
        garantirEspaco(40f);
        CategoriaFiscalizacao cat = checklist.categoria();
        ChecklistModelo modelo = cat != null ? ChecklistFiscalizacao.modelo(cat) : null;
        Map<String, Item> porId = new LinkedHashMap<>();
        if (modelo != null) {
            modelo.itens().forEach(i -> porId.put(i.id(), i));
        }

        texto(fNegrito, 9f, COR_ESCURA, MARGEM_ESQ, y,
                "Checklist estruturado (modelo v" + checklist.versao()
                        + (cat != null ? " — " + cat.rotulo() : "") + "):");
        y -= 13f;

        for (var entry : checklist.itens().entrySet()) {
            Item item = porId.get(entry.getKey());
            String rotulo = item != null ? item.rotulo() : entry.getKey();
            String resp = formatarValorChecklist(entry.getValue());
            String linha = "- " + rotulo + ": " + resp;
            for (String parte : quebrar(fRegular, 8.5f, linha, LARGURA_CONTEUDO - 12f)) {
                garantirEspaco(13f);
                texto(fRegular, 8.5f, COR_TEXTO, MARGEM_ESQ + 8, y, parte);
                y -= 11.5f;
            }
        }
        y -= 4f;
    }

    private void desenharDocumentos(List<DocumentoVinculado> docs) throws IOException {
        tituloSecao("5. DOCUMENTOS VINCULADOS (INTEGRIDADE SHA-256)");
        if (docs.isEmpty()) {
            textoSimples("Nenhum documento vinculado a esta demanda.");
            y -= 6;
            return;
        }
        int idx = 1;
        for (DocumentoVinculado docItem : docs) {
            garantirEspaco(46f);
            retanguloPreenchido(MARGEM_ESQ, y - 30, LARGURA_CONTEUDO, 38, COR_FUNDO_SUAVE, COR_BORDA);
            String titulo = idx++ + ". " + valor(docItem.titulo()) + "  [" + docItem.categoria() + " / " + docItem.tipo() + "]";
            texto(fNegrito, 8.5f, COR_ESCURA, MARGEM_ESQ + 6, y - 2, truncar(fNegrito, 8.5f, titulo, LARGURA_CONTEUDO - 12));
            String arq = "Arquivo: " + docItem.nomeArquivo() + " (" + formatarBytes(docItem.tamanho()) + ")"
                    + "  |  Data: " + data(docItem.dataDocumento())
                    + "  |  Resp.: " + (docItem.responsavel() != null ? docItem.responsavel().nome() : "—");
            texto(fRegular, 7.5f, COR_TEXTO, MARGEM_ESQ + 6, y - 13, truncar(fRegular, 7.5f, arq, LARGURA_CONTEUDO - 12));
            texto(fMono, 7.5f, COR_PRIMARIA, MARGEM_ESQ + 6, y - 24, "SHA-256: " + docItem.sha256());
            y -= 43f;
        }
        y -= 4f;
    }

    private void desenharBlocoAuditoriaEqr(DemandaDetalhe d, List<AuditoriaDto> auditorias, UsuarioAutenticado ator,
                                          LocalDateTime emitidoEm, String codigoVerificacao, String payloadQr)
            throws IOException {
        float alturaBox = 112f;
        garantirEspaco(alturaBox + 28f);
        tituloSecao("6. VERIFICAÇÃO DE AUDITORIA E QR CODE");

        float boxY = y - alturaBox;
        retanguloPreenchido(MARGEM_ESQ, boxY, LARGURA_CONTEUDO, alturaBox, COR_FUNDO_SUAVE, COR_PRIMARIA);

        float qrTam = 92f;
        float qrX = MARGEM_ESQ + LARGURA_CONTEUDO - qrTam - 10f;
        float qrY = boxY + (alturaBox - qrTam) / 2f;

        BufferedImage qrImg = gerarQr(payloadQr, 260);
        PDImageXObject pdQr = LosslessFactory.createFromImage(doc, qrImg);
        cs.drawImage(pdQr, qrX, qrY, qrTam, qrTam);

        float textoMax = qrX - MARGEM_ESQ - 20f;
        float ty = y - 16f;
        texto(fNegrito, 9f, COR_ESCURA, MARGEM_ESQ + 10, ty, "Código de verificação de auditoria:");
        ty -= 14f;
        texto(fMonoNegrito, 10.5f, COR_PRIMARIA, MARGEM_ESQ + 10, ty, codigoVerificacao);
        ty -= 15f;

        String ultAuditoria = auditorias.isEmpty()
                ? "Sem registro prévio em AUDITORIA antes desta emissão."
                : "Último registro de auditoria prévio: #" + auditorias.get(0).id()
                        + " (" + auditorias.get(0).acao() + " em " + dataHoraOu(auditorias.get(0).data(), "—") + ")";

        List<String> infos = List.of(
                "Demanda: " + d.numero() + " (ID " + d.id() + ")  |  Emitido: " + DATA_HORA_SEG.format(emitidoEm),
                "Solicitante: " + ator.nome() + " (" + ator.perfil().name() + ")",
                ultAuditoria,
                "QR Code: escaneie para ler o código de verificação da emissão demonstrativa."
        );
        for (String info : infos) {
            for (String linha : quebrar(fRegular, 7.8f, info, textoMax)) {
                texto(fRegular, 7.8f, COR_TEXTO, MARGEM_ESQ + 10, ty, linha);
                ty -= 10.5f;
            }
        }
        y = boxY - 8f;
    }

    private void carimbarFaixaERodapeEmTodasAsPaginas(String numeroDemanda, String codigoVerificacao)
            throws IOException {
        int total = doc.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage p = doc.getPage(i);
            float w = p.getMediaBox().getWidth();
            float h = p.getMediaBox().getHeight();
            try (PDPageContentStream stamp = new PDPageContentStream(doc, p, AppendMode.APPEND, true, true)) {
                // Faixa obrigatória no topo de todas as páginas
                stamp.setNonStrokingColor(COR_FAIXA_FUNDO);
                stamp.setStrokingColor(COR_FAIXA_BORDA);
                stamp.setLineWidth(1.0f);
                stamp.addRect(MARGEM_ESQ, h - 34f, w - MARGEM_ESQ - MARGEM_DIR, 20f);
                stamp.fillAndStroke();

                String faixa = sanear(fNegrito, DossieDemandaService.FAIXA_DEMONSTRATIVA);
                float largTexto = fNegrito.getStringWidth(faixa) / 1000f * 8.5f;
                float tx = MARGEM_ESQ + Math.max(6f, (w - MARGEM_ESQ - MARGEM_DIR - largTexto) / 2f);
                stamp.beginText();
                stamp.setFont(fNegrito, 8.5f);
                stamp.setNonStrokingColor(COR_FAIXA_TEXTO);
                stamp.newLineAtOffset(tx, h - 27f);
                stamp.showText(faixa);
                stamp.endText();

                // Rodapé
                stamp.setStrokingColor(COR_BORDA);
                stamp.setLineWidth(0.6f);
                stamp.moveTo(MARGEM_ESQ, 32f);
                stamp.lineTo(w - MARGEM_DIR, 32f);
                stamp.stroke();

                String esq = sanear(fRegular, "SEMA ALERTA · Dossiê " + numeroDemanda + " · Verificação: " + codigoVerificacao);
                String dir = "Página " + (i + 1) + " de " + total;
                stamp.beginText();
                stamp.setFont(fRegular, 7.5f);
                stamp.setNonStrokingColor(COR_SUAVE);
                stamp.newLineAtOffset(MARGEM_ESQ, 20f);
                stamp.showText(esq);
                stamp.endText();

                float dirW = fRegular.getStringWidth(dir) / 1000f * 7.5f;
                stamp.beginText();
                stamp.setFont(fRegular, 7.5f);
                stamp.setNonStrokingColor(COR_SUAVE);
                stamp.newLineAtOffset(w - MARGEM_DIR - dirW, 20f);
                stamp.showText(dir);
                stamp.endText();
            }
        }
    }

    // ------------------------------------------------------------------ primitivas e paginação

    private void novaPagina() throws IOException {
        fecharFluxoAtual();
        paginaAtual = new PDPage(PDRectangle.A4);
        doc.addPage(paginaAtual);
        cs = new PDPageContentStream(doc, paginaAtual);
        y = Y_INICIO;
    }

    private void fecharFluxoAtual() throws IOException {
        if (cs != null) {
            cs.close();
            cs = null;
        }
    }

    private void garantirEspaco(float necessario) throws IOException {
        if (y - necessario < Y_LIMITE) {
            novaPagina();
        }
    }

    private void tituloSecao(String titulo) throws IOException {
        garantirEspaco(28f);
        texto(fNegrito, 9.5f, COR_PRIMARIA, MARGEM_ESQ, y, titulo);
        y -= 5f;
        cs.setStrokingColor(COR_PRIMARIA);
        cs.setLineWidth(1.0f);
        linha(MARGEM_ESQ, y, MARGEM_ESQ + LARGURA_CONTEUDO, y);
        y -= 13f;
    }

    private void linhaDupla(String rotulo1, String valor1, String rotulo2, String valor2) throws IOException {
        float colW = (LARGURA_CONTEUDO - 14f) / 2f;
        List<String> l1 = quebrar(fRegular, 8.5f, valor(valor1), colW);
        List<String> l2 = quebrar(fRegular, 8.5f, valor(valor2), colW);
        int linhas = Math.max(l1.size(), l2.size());
        garantirEspaco(12f + linhas * 11f + 4f);

        texto(fNegrito, 7.5f, COR_SUAVE, MARGEM_ESQ, y, rotulo1.toUpperCase(PT_BR));
        texto(fNegrito, 7.5f, COR_SUAVE, MARGEM_ESQ + colW + 14f, y, rotulo2.toUpperCase(PT_BR));
        y -= 10f;
        for (int i = 0; i < linhas; i++) {
            if (i < l1.size()) {
                texto(fRegular, 8.5f, COR_TEXTO, MARGEM_ESQ, y, l1.get(i));
            }
            if (i < l2.size()) {
                texto(fRegular, 8.5f, COR_TEXTO, MARGEM_ESQ + colW + 14f, y, l2.get(i));
            }
            y -= 11f;
        }
        y -= 2f;
    }

    private void blocoTexto(String rotulo, String conteudo) throws IOException {
        List<String> linhas = quebrar(fRegular, 8.5f, valor(conteudo), LARGURA_CONTEUDO - 8f);
        garantirEspaco(24f);
        texto(fNegrito, 7.5f, COR_SUAVE, MARGEM_ESQ, y, rotulo.toUpperCase(PT_BR));
        y -= 10f;
        for (String l : linhas) {
            garantirEspaco(13f);
            texto(fRegular, 8.5f, COR_TEXTO, MARGEM_ESQ + 4f, y, l);
            y -= 11f;
        }
        y -= 3f;
    }

    private void textoSimples(String msg) throws IOException {
        garantirEspaco(15f);
        texto(fRegular, 8.5f, COR_SUAVE, MARGEM_ESQ, y, msg);
        y -= 14f;
    }

    private void texto(PDFont fonte, float tamanho, Color cor, float x, float yPos, String texto) throws IOException {
        String seguro = sanear(fonte, texto);
        cs.beginText();
        cs.setFont(fonte, tamanho);
        cs.setNonStrokingColor(cor);
        cs.newLineAtOffset(x, yPos);
        cs.showText(seguro);
        cs.endText();
    }

    private void retanguloPreenchido(float x, float yBase, float w, float h, Color fill, Color stroke)
            throws IOException {
        cs.setNonStrokingColor(fill);
        cs.setStrokingColor(stroke);
        cs.setLineWidth(0.75f);
        cs.addRect(x, yBase, w, h);
        cs.fillAndStroke();
    }

    private void linha(float x1, float y1, float x2, float y2) throws IOException {
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private void circulo(float cx, float cy, float r, boolean preencher) throws IOException {
        float k = 0.55228475f * r;
        cs.moveTo(cx + r, cy);
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        cs.closePath();
        if (preencher) {
            cs.fill();
        } else {
            cs.stroke();
        }
    }

    private void desenharAlvo(float px, float py, Color cor) throws IOException {
        cs.setStrokingColor(cor);
        cs.setLineWidth(1.1f);
        circulo(px, py, 4.5f, false);
        cs.setNonStrokingColor(cor);
        circulo(px, py, 2.0f, true);
    }

    // ------------------------------------------------------------------ geometria e QR

    @SuppressWarnings("unchecked")
    private static List<List<List<double[]>>> extrairPoligonos(Map<String, Object> geom) {
        if (geom == null) {
            return List.of();
        }
        Object tipo = geom.get("type");
        Object coordsObj = geom.get("coordinates");
        if (!(coordsObj instanceof List<?> coords) || coords.isEmpty()) {
            return List.of();
        }
        List<List<List<double[]>>> out = new ArrayList<>();
        if ("Polygon".equals(tipo)) {
            List<List<double[]>> poly = lerPoligono(coords);
            if (!poly.isEmpty()) {
                out.add(poly);
            }
        } else if ("MultiPolygon".equals(tipo)) {
            for (Object item : coords) {
                if (item instanceof List<?> lp) {
                    List<List<double[]>> poly = lerPoligono(lp);
                    if (!poly.isEmpty()) {
                        out.add(poly);
                    }
                }
            }
        }
        return out;
    }

    private static List<List<double[]>> lerPoligono(List<?> aneis) {
        List<List<double[]>> saida = new ArrayList<>();
        for (Object anelObj : aneis) {
            if (!(anelObj instanceof List<?> pts)) {
                continue;
            }
            List<double[]> anel = new ArrayList<>();
            for (Object pObj : pts) {
                if (pObj instanceof List<?> c && c.size() >= 2
                        && c.get(0) instanceof Number lon && c.get(1) instanceof Number lat) {
                    anel.add(new double[] {lon.doubleValue(), lat.doubleValue()});
                }
            }
            if (anel.size() >= 3) {
                saida.add(anel);
            }
        }
        return saida;
    }

    private static double[] pontoReferencia(DemandaDetalhe d, List<List<List<double[]>>> poligonos) {
        if (d.latitude() != null && d.longitude() != null) {
            return new double[] {d.latitude().doubleValue(), d.longitude().doubleValue()};
        }
        if (d.areaGeometria() != null && !poligonos.isEmpty()) {
            return Geodesia.centroide(d.areaGeometria());
        }
        return null;
    }

    private static float mapX(double lon, double lonMin, double lonMax, float x, float w) {
        return (float) (x + ((lon - lonMin) / (lonMax - lonMin)) * w);
    }

    private static float mapY(double lat, double latMin, double latMax, float yBase, float h) {
        return (float) (yBase + ((lat - latMin) / (latMax - latMin)) * h);
    }

    private static BufferedImage gerarQr(String payload, int tamanhoPx) {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.CHARACTER_SET, "UTF-8",
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, 2);
        try {
            BitMatrix matrix = writer.encode(payload, BarcodeFormat.QR_CODE, tamanhoPx, tamanhoPx, hints);
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (WriterException e) {
            throw new IllegalStateException("Falha ao codificar QR Code do dossiê.", e);
        }
    }

    // ------------------------------------------------------------------ texto e formatação

    private static List<String> quebrar(PDFont fonte, float tamanho, String texto, float larguraMax)
            throws IOException {
        String limpo = sanearComQuebras(fonte, texto);
        if (limpo.isEmpty()) {
            return List.of("—");
        }
        List<String> linhas = new ArrayList<>();
        for (String paragrafo : limpo.split("\n", -1)) {
            if (paragrafo.isBlank()) {
                linhas.add("");
                continue;
            }
            String[] palavras = paragrafo.trim().split("\\s+");
            StringBuilder atual = new StringBuilder();
            for (String palavra : palavras) {
                if (atual.isEmpty()) {
                    if (largura(fonte, tamanho, palavra) <= larguraMax) {
                        atual.append(palavra);
                    } else {
                        for (String pedaco : partirPalavra(fonte, tamanho, palavra, larguraMax)) {
                            linhas.add(pedaco);
                        }
                    }
                } else {
                    String candidato = atual + " " + palavra;
                    if (largura(fonte, tamanho, candidato) <= larguraMax) {
                        atual.append(' ').append(palavra);
                    } else {
                        linhas.add(atual.toString());
                        atual.setLength(0);
                        if (largura(fonte, tamanho, palavra) <= larguraMax) {
                            atual.append(palavra);
                        } else {
                            List<String> pedacos = partirPalavra(fonte, tamanho, palavra, larguraMax);
                            for (int i = 0; i < pedacos.size() - 1; i++) {
                                linhas.add(pedacos.get(i));
                            }
                            atual.append(pedacos.get(pedacos.size() - 1));
                        }
                    }
                }
            }
            if (!atual.isEmpty()) {
                linhas.add(atual.toString());
            }
        }
        return linhas.isEmpty() ? List.of("—") : linhas;
    }

    private static List<String> partirPalavra(PDFont fonte, float tamanho, String palavra, float larguraMax)
            throws IOException {
        List<String> partes = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < palavra.length(); i++) {
            char c = palavra.charAt(i);
            String cand = sb.toString() + c;
            if (!sb.isEmpty() && largura(fonte, tamanho, cand) > larguraMax) {
                partes.add(sb.toString());
                sb.setLength(0);
            }
            sb.append(c);
        }
        if (!sb.isEmpty()) {
            partes.add(sb.toString());
        }
        return partes;
    }

    private static String truncar(PDFont fonte, float tamanho, String texto, float larguraMax) throws IOException {
        String s = sanear(fonte, texto);
        if (largura(fonte, tamanho, s) <= larguraMax) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            String cand = sb.toString() + s.charAt(i) + "...";
            if (largura(fonte, tamanho, cand) > larguraMax) {
                return sb + "...";
            }
            sb.append(s.charAt(i));
        }
        return s;
    }

    private static float largura(PDFont fonte, float tamanho, String texto) throws IOException {
        return fonte.getStringWidth(texto) / 1000f * tamanho;
    }

    /** Normaliza quebras e substitui caracteres fora de WinAnsiEncoding para evitar exceção no PDFBox. */
    private static String sanear(PDFont fonte, String texto) {
        return sanearComQuebras(fonte, texto).replace('\n', ' ');
    }

    private static String sanearComQuebras(PDFont fonte, String texto) {
        if (texto == null) {
            return "";
        }
        String norm = texto.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ')
                .replace("→", "->").replace("←", "<-").replace("↔", "<->")
                .replace("≤", "<=").replace("≥", ">=").replace("≠", "!=")
                .replace("…", "...").replace("\u00A0", " ");
        StringBuilder sb = new StringBuilder(norm.length());
        for (int i = 0; i < norm.length(); ) {
            int cp = norm.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\n') {
                sb.append('\n');
                continue;
            }
            if (Character.isISOControl(cp)) {
                continue;
            }
            String ch = new String(Character.toChars(cp));
            try {
                fonte.encode(ch);
                sb.append(ch);
            } catch (IllegalArgumentException | IOException e) {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static String valor(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static String valorOu(String s, String padrao) {
        return s == null || s.isBlank() ? padrao : s;
    }

    private static String data(LocalDate d) {
        return d == null ? "—" : DATA.format(d);
    }

    private static String dataOu(LocalDate d, String padrao) {
        return d == null ? padrao : DATA.format(d);
    }

    private static String dataHoraOu(LocalDateTime dt, String padrao) {
        return dt == null ? padrao : DATA_HORA.format(dt);
    }

    private static String formatarCoord(BigDecimal v) {
        return String.format(Locale.US, "%.6f", v);
    }

    private static String formatarNumero(BigDecimal v) {
        NumberFormat nf = NumberFormat.getNumberInstance(PT_BR);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(3);
        return nf.format(v);
    }

    private static String formatarBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(PT_BR, "%.1f KB", bytes / 1024.0);
        }
        return String.format(PT_BR, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private static String formatarValorChecklist(Object valor) {
        if (valor == null) {
            return "Não respondido";
        }
        if (valor instanceof Boolean b) {
            return b ? "Sim" : "Não";
        }
        if (valor instanceof Number n) {
            return NumberFormat.getNumberInstance(PT_BR).format(n);
        }
        return String.valueOf(valor);
    }
}
