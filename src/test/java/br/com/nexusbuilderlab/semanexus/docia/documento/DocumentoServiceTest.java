package br.com.nexusbuilderlab.semanexus.docia.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoDetalhe;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoUploadRequest;
import br.com.nexusbuilderlab.semanexus.docia.storage.StorageService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class DocumentoServiceTest {

    StorageService storage = mock(StorageService.class);
    DocumentoRepository documentos = mock(DocumentoRepository.class);
    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    AuditService auditoria = mock(AuditService.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    DocumentoService service;
    UsuarioAutenticado ator = new UsuarioAutenticado(3L, "Analista", "a@demo.local", Perfil.ANALISTA);

    @BeforeEach
    void preparar() throws IOException {
        service = new DocumentoService(documentos, mock(MunicipioRepository.class), usuarios, storage,
                new TextExtractor(), auditoria, mock(DemandaService.class), jdbc);
        when(storage.store(any())).thenReturn("2026/11/uuid");
        when(usuarios.findById(3L)).thenReturn(Optional.of(mock(Usuario.class)));
        when(documentos.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jdbc.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
    }

    private static DocumentoUploadRequest pedido(String nome, byte[] bytes) {
        return new DocumentoUploadRequest(new MockMultipartFile("arquivo", nome, "application/octet-stream", bytes),
                "Laudo de teste", null, "Relatório", "Laudo", LocalDate.now(), null, List.of("a", "b"), null);
    }

    private static byte[] pdf(String texto) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            doc.addPage(pagina);
            try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(texto);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void pdfValidoGravaNoVolumeEExtraiTexto() throws IOException {
        DocumentoDetalhe d = service.enviar(ator, pedido("laudo.PDF", pdf("Vistoria fictícia em campo")));
        assertThat(d.textoExtraido()).contains("Vistoria");
        assertThat(d.mime()).isEqualTo("application/pdf");
        assertThat(d.sha256()).hasSize(64);
        assertThat(d.palavrasChave()).containsExactly("a", "b");
        verify(storage).store(any());
        verify(auditoria).registrar(org.mockito.ArgumentMatchers.eq("UPLOAD"), any(), any(), any());
    }

    @Test
    void txtValidoExtraiTextoDireto() {
        DocumentoDetalhe d = service.enviar(ator, pedido("notas.txt", "Texto de acentuação\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(d.textoExtraido()).isEqualTo("Texto de acentuação");
    }

    @Test
    void docxNaoExtraiTexto() {
        DocumentoDetalhe d = service.enviar(ator, pedido("a.docx", new byte[] {'P', 'K', 3, 4, 1, 2}));
        assertThat(d.textoExtraido()).isNull();
    }

    @Test
    void extensaoNaoPermitidaResponde415SemGravar() throws IOException {
        assertThatThrownBy(() -> service.enviar(ator, pedido("virus.exe", "MZ".getBytes())))
                .isInstanceOf(ArquivoNaoSuportadoException.class);
        verify(storage, never()).store(any());
    }

    @Test
    void extensaoFalsaPdfComConteudoExecutavelResponde415() throws IOException {
        assertThatThrownBy(() -> service.enviar(ator, pedido("laudo.pdf", new byte[] {'M', 'Z', 0, 1, 2, 3})))
                .isInstanceOf(ArquivoNaoSuportadoException.class);
        assertThatThrownBy(() -> service.enviar(ator, pedido("nota.txt", new byte[] {'M', 'Z', 0, 1, 2, 3})))
                .isInstanceOf(ArquivoNaoSuportadoException.class);
        verify(storage, never()).store(any());
    }

    @Test
    void arquivoAcimaDe10MbResponde413() {
        byte[] grande = new byte[10 * 1024 * 1024 + 1];
        java.util.Arrays.fill(grande, (byte) 'a');
        assertThatThrownBy(() -> service.enviar(ator, pedido("grande.txt", grande)))
                .isInstanceOf(ArquivoMuitoGrandeException.class);
    }

    @Test
    void nomeComTravessiaFicaSoComNomeBaseEOCaminhoNoVolumeEGerado() throws IOException {
        DocumentoDetalhe d = service.enviar(ator, pedido("../../etc/passwd/..\\segredo.txt", "ok".getBytes()));
        assertThat(d.nomeArquivo()).isEqualTo("segredo.txt");
        ArgumentCaptor<byte[]> conteudo = ArgumentCaptor.forClass(byte[].class);
        verify(storage).store(conteudo.capture()); // a interface não recebe nome: o caminho nunca deriva dele
        assertThat(DocumentoService.sanearNome("../x/../y.pdf")).isEqualTo("y.pdf");
    }

    @Test
    void falhaAposGravarRemoveOArquivoOrfao() throws IOException {
        when(documentos.save(any())).thenThrow(new IllegalStateException("falha"));
        assertThatThrownBy(() -> service.enviar(ator, pedido("a.txt", "ok".getBytes())))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).delete("2026/11/uuid");
    }

    @Test
    void normalizarRemoveAcentosECaixa() {
        assertThat(DocumentoSpecification.normalizar("FISCALIZAÇÃO Ação")).isEqualTo("fiscalizacao acao");
        assertThat(DocumentoSpecification.escaparLike("10%_a")).isEqualTo("10\\%\\_a");
    }

    @Test
    void vincularDuplicadoResponde409SemInserir() {
        Documento d = mock(Documento.class);
        when(d.getTitulo()).thenReturn("Laudo");
        when(documentos.buscarAtivo(7L)).thenReturn(Optional.of(d));
        when(jdbc.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        assertThatThrownBy(() -> service.vincular(ator, 1L, 7L))
                .isInstanceOf(br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException.class);
        verify(jdbc, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void desvincularInexistenteResponde404() {
        Documento d = mock(Documento.class);
        when(documentos.buscarAtivo(7L)).thenReturn(Optional.of(d));
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> service.desvincular(ator, 1L, 7L))
                .isInstanceOf(br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException.class);
    }
}
