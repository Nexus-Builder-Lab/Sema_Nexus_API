package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.dto.EquipeRef;
import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.AuditoriaDto;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoService;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoService.DocumentoVinculado;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ChecklistPreenchido;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FiscalizacaoDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FotoFiscalizacao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FotoFiscalizacao.ConferenciaExif;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.HistoricoItem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** Testes unitários da geração do dossiê em PDF (Issue #146): PDFBox, faixa, croqui, SHA-256 e QR Code ZXing. */
class DossieDemandaServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T16:30:00Z"), ZoneOffset.UTC);
    private static final UsuarioAutenticado ATOR =
            new UsuarioAutenticado(1L, "Gestor Demo", "gestor@demo.semanexus.local", Perfil.GESTOR);

    private DemandaService demandas;
    private DocumentoService documentos;
    private FotoFiscalizacaoService fotos;
    private AuditService auditoria;
    private DossieDemandaService service;

    @BeforeEach
    void setup() {
        demandas = mock(DemandaService.class);
        documentos = mock(DocumentoService.class);
        fotos = mock(FotoFiscalizacaoService.class);
        auditoria = mock(AuditService.class);
        service = new DossieDemandaService(demandas, documentos, fotos, auditoria, CLOCK);
    }

    @Test
    void geraPdfCompletoComFaixaCroquiChecklistDocumentosSha256EQrDecodificavel() throws Exception {
        Map<String, Object> poligono = Map.of(
                "type", "Polygon",
                "coordinates", List.of(
                        List.of(
                                List.of(-56.120, -15.610),
                                List.of(-56.080, -15.610),
                                List.of(-56.080, -15.580),
                                List.of(-56.120, -15.580),
                                List.of(-56.120, -15.610)),
                        List.of(
                                List.of(-56.105, -15.600),
                                List.of(-56.095, -15.600),
                                List.of(-56.095, -15.590),
                                List.of(-56.105, -15.590),
                                List.of(-56.105, -15.600))));

        Map<String, Object> itensChecklist = new LinkedHashMap<>();
        itensChecklist.put("local_acessivel", true);
        itensChecklist.put("supressao_confirmada", true);
        itensChecklist.put("area_estimada_ha", 42.35);
        itensChecklist.put("maquinario_no_local", false);
        itensChecklist.put("observacoes_checklist", "Trilha aberta com acesso pela vicinal norte.");
        ChecklistPreenchido checklist = new ChecklistPreenchido(1, CategoriaFiscalizacao.DESMATAMENTO_ILEGAL, itensChecklist);

        FiscalizacaoDetalhe fiscalizacao = new FiscalizacaoDetalhe(
                4L, LocalDate.of(2026, 11, 18), new EquipeRef(2L, "Equipe Cerrado Norte"),
                "Vistoria in loco com georreferenciamento da área.",
                "Constatada supressão em talhão de cerrado.",
                "Medição em campo e registro fotográfico.",
                "Encaminhar para análise técnica complementar.",
                new UsuarioRef(3L, "Fiscal Demo"), LocalDateTime.of(2026, 11, 18, 15, 20), checklist);

        DemandaDetalhe demanda = new DemandaDetalhe(
                10L, "DEM-2026/0010", "Supressão irregular em borda de reserva",
                "Relato de abertura de área sem autorização prévia em região rural.",
                Origem.DENUNCIA, CategoriaFiscalizacao.DESMATAMENTO_ILEGAL,
                new MunicipioRef(5103403, "Cuiabá", Bioma.CERRADO), "Região do Coxipó do Ouro",
                new BigDecimal("-15.595000"), new BigDecimal("-56.100000"), new BigDecimal("42.35"), poligono,
                LocalDate.of(2026, 11, 10), Prioridade.ALTA, LocalDate.of(2026, 11, 25),
                LocalDate.of(2026, 11, 30), StatusDemanda.AGUARDANDO_CONCLUSAO, false,
                new EquipeRef(2L, "Equipe Cerrado Norte"), new UsuarioRef(3L, "Fiscal Demo"),
                "Prioridade alta pelo histórico recente.", null, null, null, fiscalizacao, 1,
                new UsuarioRef(1L, "Gestor Demo"), LocalDateTime.of(2026, 11, 10, 9, 0),
                LocalDateTime.of(2026, 11, 18, 15, 20));

        List<HistoricoItem> historico = List.of(
                new HistoricoItem(3L, TipoHistorico.FISCALIZACAO, StatusDemanda.EM_ANDAMENTO,
                        StatusDemanda.AGUARDANDO_CONCLUSAO, "Fiscalização registrada em campo.",
                        new UsuarioRef(3L, "Fiscal Demo"), LocalDateTime.of(2026, 11, 18, 15, 20)),
                new HistoricoItem(2L, TipoHistorico.DISTRIBUICAO, StatusDemanda.AGUARDANDO_DISTRIBUICAO,
                        StatusDemanda.DISTRIBUIDA, "Distribuída para Equipe Cerrado Norte.",
                        new UsuarioRef(1L, "Gestor Demo"), LocalDateTime.of(2026, 11, 11, 10, 15)),
                new HistoricoItem(1L, TipoHistorico.CADASTRO, null, StatusDemanda.PENDENTE,
                        "Demanda cadastrada.", new UsuarioRef(1L, "Gestor Demo"), LocalDateTime.of(2026, 11, 10, 9, 0)));

        String shaDoc = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        List<DocumentoVinculado> docs = List.of(
                new DocumentoVinculado(7L, "Laudo Técnico Preliminar", "Relatório técnico", "Laudo",
                        LocalDate.of(2026, 11, 19), "laudo-preliminar.pdf", "application/pdf",
                        184320L, shaDoc, new UsuarioRef(4L, "Analista Demo"), LocalDateTime.of(2026, 11, 19, 11, 0)));

        String shaFoto = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
        List<FotoFiscalizacao> fotosList = List.of(
                new FotoFiscalizacao(9L, 10L, "vistoria-01.jpg", "image/jpeg", 98304L, shaFoto,
                        LocalDateTime.of(2026, 11, 18, 14, 10), new BigDecimal("-15.595100"),
                        new BigDecimal("-56.100200"), new BigDecimal("0.024"), false,
                        ConferenciaExif.CONFERE, new UsuarioRef(3L, "Fiscal Demo"),
                        LocalDateTime.of(2026, 11, 18, 15, 5)));

        List<AuditoriaDto> audits = List.of(
                new AuditoriaDto(88L, LocalDateTime.of(2026, 11, 18, 15, 20), 3L, "Fiscal Demo",
                        "REGISTRAR_FISCALIZACAO", "DEMANDA", "10", "Fiscalização registrada"));

        when(demandas.detalhar(ATOR, 10L)).thenReturn(demanda);
        when(demandas.historico(ATOR, 10L)).thenReturn(historico);
        when(documentos.listarVinculadosParaDossie(ATOR, 10L)).thenReturn(docs);
        when(fotos.listar(ATOR, 10L)).thenReturn(fotosList);
        when(auditoria.historicoDaEntidade("DEMANDA", "10")).thenReturn(audits);

        DossieDemandaService.PdfDossie resultado = service.gerar(ATOR, 10L);

        assertThat(resultado.nomeArquivo()).isEqualTo("dossie-DEM-2026-0010.pdf");
        assertThat(resultado.codigoVerificacao()).matches("[A-F0-9]{6}-[A-F0-9]{6}-[A-F0-9]{6}-[A-F0-9]{6}");
        assertThat(resultado.conteudo()).startsWith("%PDF-".getBytes());

        verify(auditoria).registrar(eq("EXPORTAR_DOSSIE"), eq("DEMANDA"), eq("10"),
                contains(resultado.codigoVerificacao()));

        try (PDDocument pdf = Loader.loadPDF(resultado.conteudo())) {
            int paginas = pdf.getNumberOfPages();
            assertThat(paginas).isGreaterThanOrEqualTo(1);

            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= paginas; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                assertThat(stripper.getText(pdf))
                        .as("faixa demonstrativa na página %d", p)
                        .contains(DossieDemandaService.FAIXA_DEMONSTRATIVA);
            }

            stripper.setStartPage(1);
            stripper.setEndPage(paginas);
            String textoCompleto = stripper.getText(pdf);
            assertThat(textoCompleto)
                    .contains("DEM-2026/0010")
                    .contains("Cuiabá")
                    .contains("Equipe Cerrado Norte")
                    .contains("A supressão de vegetação foi confirmada?")
                    .contains("Laudo Técnico Preliminar")
                    .contains(shaDoc)
                    .contains(shaFoto)
                    .contains(resultado.codigoVerificacao());

            // Renderiza a última página (onde está o bloco de auditoria + QR Code) e decodifica com ZXing
            BufferedImage img = new PDFRenderer(pdf).renderImageWithDPI(paginas - 1, 200);
            Result qr = new QRCodeReader().decode(
                    new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img))));
            assertThat(qr.getText())
                    .contains("DEM-2026/0010")
                    .contains("DEMANDA=10")
                    .contains("VERIFICACAO=" + resultado.codigoVerificacao());
        }
    }

    @Test
    void geraPdfMinimoSemPoligonoESuportaCaracteresForaWinAnsi() throws Exception {
        DemandaDetalhe demanda = new DemandaDetalhe(
                22L, "DEM-2026/0022", "Ocorrência pontual com símbolos → ≤ ≥",
                "Descrição com seta → e símbolo ≤ 10 ha e emoji 🔍.",
                Origem.MONITORAMENTO, null,
                new MunicipioRef(5107909, "Sinop", Bioma.AMAZONIA), null,
                new BigDecimal("-11.864200"), new BigDecimal("-55.502500"), null, null,
                LocalDate.of(2026, 11, 20), null, null, null,
                StatusDemanda.PENDENTE, false, null, null, null, null, null, null, null, 0,
                new UsuarioRef(1L, "Gestor Demo"), LocalDateTime.of(2026, 11, 20, 8, 30), null);

        when(demandas.detalhar(ATOR, 22L)).thenReturn(demanda);
        when(demandas.historico(ATOR, 22L)).thenReturn(List.of());
        when(documentos.listarVinculadosParaDossie(ATOR, 22L)).thenReturn(List.of());
        when(fotos.listar(ATOR, 22L)).thenReturn(List.of());
        when(auditoria.historicoDaEntidade("DEMANDA", "22")).thenReturn(List.of());

        DossieDemandaService.PdfDossie resultado = service.gerar(ATOR, 22L);
        try (PDDocument pdf = Loader.loadPDF(resultado.conteudo())) {
            String texto = new PDFTextStripper().getText(pdf);
            assertThat(texto)
                    .contains(DossieDemandaService.FAIXA_DEMONSTRATIVA)
                    .contains("DEM-2026/0022")
                    .contains("Sinop")
                    .contains("Nenhum documento vinculado");
            BufferedImage img = new PDFRenderer(pdf).renderImageWithDPI(pdf.getNumberOfPages() - 1, 200);
            Result qr = new QRCodeReader().decode(
                    new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img))));
            assertThat(qr.getText()).contains(resultado.codigoVerificacao());
        }
    }

    @Test
    void geraPdfComMultiPolygonESemCoordenadaExplicita() throws Exception {
        Map<String, Object> multi = Map.of(
                "type", "MultiPolygon",
                "coordinates", List.of(
                        List.of(List.of(
                                List.of(-55.50, -11.90), List.of(-55.45, -11.90),
                                List.of(-55.45, -11.85), List.of(-55.50, -11.85), List.of(-55.50, -11.90))),
                        List.of(List.of(
                                List.of(-55.42, -11.90), List.of(-55.39, -11.90),
                                List.of(-55.39, -11.87), List.of(-55.42, -11.87), List.of(-55.42, -11.90)))));
        DemandaDetalhe demanda = new DemandaDetalhe(
                33L, "DEM-2026/0033", "Área fragmentada em MultiPolygon",
                "Dois talhões distintos monitorados por satélite.",
                Origem.ANALISE_AMBIENTAL, CategoriaFiscalizacao.QUEIMADA_INCENDIO,
                new MunicipioRef(5107909, "Sinop", Bioma.AMAZONIA), null,
                null, null, new BigDecimal("250.00"), multi,
                LocalDate.of(2026, 11, 21), Prioridade.MEDIA, null, null,
                StatusDemanda.EM_TRIAGEM, false, null, null, null, null, null, null, null, 0,
                new UsuarioRef(1L, "Gestor Demo"), LocalDateTime.of(2026, 11, 21, 10, 0), null);

        when(demandas.detalhar(ATOR, 33L)).thenReturn(demanda);
        when(demandas.historico(ATOR, 33L)).thenReturn(List.of());
        when(documentos.listarVinculadosParaDossie(ATOR, 33L)).thenReturn(List.of());
        when(fotos.listar(ATOR, 33L)).thenReturn(List.of());
        when(auditoria.historicoDaEntidade("DEMANDA", "33")).thenReturn(List.of());

        DossieDemandaService.PdfDossie resultado = service.gerar(ATOR, 33L);
        try (PDDocument pdf = Loader.loadPDF(resultado.conteudo())) {
            String texto = new PDFTextStripper().getText(pdf);
            assertThat(texto).contains("2 polígonos").contains("250,00 ha");
        }
    }

    @Test
    void propaga404E403SemAuditar() {
        when(demandas.detalhar(ATOR, 999L)).thenThrow(RecursoNaoEncontradoException.de("Demanda", 999L));
        assertThatThrownBy(() -> service.gerar(ATOR, 999L))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        UsuarioAutenticado fiscal = new UsuarioAutenticado(5L, "Outro Fiscal", "f@demo.test", Perfil.FISCAL);
        when(demandas.detalhar(fiscal, 10L)).thenThrow(new AccessDeniedException("Demanda fora do escopo do fiscal."));
        assertThatThrownBy(() -> service.gerar(fiscal, 10L))
                .isInstanceOf(AccessDeniedException.class);

        verify(auditoria, never()).registrar(anyString(), anyString(), anyString(), anyString());
    }
}
