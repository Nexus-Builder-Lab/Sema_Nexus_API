package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.ArquivoMuitoGrandeException;
import br.com.nexusbuilderlab.semanexus.docia.documento.ArquivoNaoSuportadoException;
import br.com.nexusbuilderlab.semanexus.docia.storage.StorageService;
import br.com.nexusbuilderlab.semanexus.docia.storage.TipoImagem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FotoFiscalizacao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FotoFiscalizacao.ConferenciaExif;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FotoFiscalizacaoServiceTest {

    /** Polígono fictício em torno de Cuiabá (~2 km de lado). */
    static final String AREA_CUIABA = "{\"type\":\"Polygon\",\"coordinates\":[[[-56.11,-15.61],[-56.09,-15.61],"
            + "[-56.09,-15.59],[-56.11,-15.59],[-56.11,-15.61]]]}";

    @Mock DemandaRepository demandas;
    @Mock DemandaHistoricoRepository historicos;
    @Mock FiscalizacaoFotoRepository fotos;
    @Mock UsuarioRepository usuarios;
    @Mock DemandaService demandaService;
    @Mock StorageService storage;
    @Mock AuditService auditoria;

    FotoFiscalizacaoService service;
    Usuario gestor;
    Usuario fiscal;
    Usuario outroFiscal;

    @BeforeEach
    void preparar() throws IOException {
        service = new FotoFiscalizacaoService(demandas, historicos, fotos, usuarios, demandaService, storage, auditoria);
        gestor = usuario(10L, Perfil.GESTOR);
        fiscal = usuario(20L, Perfil.FISCAL);
        outroFiscal = usuario(21L, Perfil.FISCAL);
        when(usuarios.findById(10L)).thenReturn(Optional.of(gestor));
        when(usuarios.findById(20L)).thenReturn(Optional.of(fiscal));
        when(usuarios.findById(21L)).thenReturn(Optional.of(outroFiscal));
        when(storage.validarImagem(any())).thenCallRealMethod();
        when(storage.store(any())).thenReturn("2026/11/chave-opaca");
        when(fotos.save(any())).thenAnswer(i -> {
            FiscalizacaoFoto f = i.getArgument(0);
            ReflectionTestUtils.setField(f, "id", 7L);
            ReflectionTestUtils.setField(f, "criadoEm", LocalDateTime.of(2026, 11, 20, 10, 0));
            return f;
        });
    }

    private static Usuario usuario(long id, Perfil perfil) {
        Usuario u = new Usuario("Usuário " + id, "u" + id + "@exemplo.test", "hash", perfil);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Demanda demanda(StatusDemanda status) {
        Demanda d = new Demanda("DEM-2026/0001", gestor);
        ReflectionTestUtils.setField(d, "id", 5L);
        d.setStatus(status);
        d.setMunicipio(mock(Municipio.class));
        d.setFiscal(fiscal);
        d.setLatitude(new BigDecimal("-15.601"));
        d.setLongitude(new BigDecimal("-56.097"));
        when(demandas.buscarParaEscrita(5L)).thenReturn(Optional.of(d));
        return d;
    }

    static byte[] recurso(String nome) throws IOException {
        try (InputStream in = FotoFiscalizacaoServiceTest.class.getResourceAsStream("/fotos/" + nome)) {
            return in.readAllBytes();
        }
    }

    private static MockMultipartFile arquivo(String nome, byte[] conteudo) {
        return new MockMultipartFile("arquivo", nome, "image/jpeg", conteudo);
    }

    private FiscalizacaoFoto fotoSalva() {
        ArgumentCaptor<FiscalizacaoFoto> c = ArgumentCaptor.forClass(FiscalizacaoFoto.class);
        verify(fotos).save(c.capture());
        return c.getValue();
    }

    @Test
    void fotoComGpsDoLocalConfereEGuardaShaEExif() throws IOException {
        demanda(StatusDemanda.EM_ANDAMENTO);
        byte[] bytes = recurso("gps-cuiaba.jpg");
        FotoFiscalizacao r = service.enviar(UsuarioAutenticado.de(fiscal), 5L, arquivo("../../etc/vistoria.jpg", bytes));

        FiscalizacaoFoto f = fotoSalva();
        assertThat(f.getMime()).isEqualTo("image/jpeg");
        assertThat(f.getNomeOriginal()).isEqualTo("vistoria.jpg");
        assertThat(f.getSha256()).hasSize(64).matches("[0-9a-f]+");
        assertThat(f.getExifDataHora()).isEqualTo(LocalDateTime.of(2026, 11, 20, 9, 30, 15));
        assertThat(f.getExifLatitude().doubleValue()).isCloseTo(-15.601, org.assertj.core.data.Offset.offset(0.0005));
        assertThat(f.getExifLongitude().doubleValue()).isCloseTo(-56.097, org.assertj.core.data.Offset.offset(0.0005));
        assertThat(f.getDistanciaKm().doubleValue()).isLessThan(0.1);
        assertThat(f.isGpsForaMt()).isFalse();
        assertThat(r.conferencia()).isEqualTo(ConferenciaExif.CONFERE);
        verify(storage).store(bytes); // gravado intacto (EXIF não é removido)
        ArgumentCaptor<DemandaHistorico> h = ArgumentCaptor.forClass(DemandaHistorico.class);
        verify(historicos).save(h.capture());
        assertThat(h.getValue().getTipo()).isEqualTo(TipoHistorico.ATIVIDADE);
        assertThat(h.getValue().getTexto()).startsWith("[Foto]");
        verify(auditoria).registrar(org.mockito.ArgumentMatchers.eq("UPLOAD_FOTO_FISCALIZACAO"), anyString(),
                org.mockito.ArgumentMatchers.eq("5"), anyString());
    }

    @Test
    void gpsForaDeMtEhSinalizadoMasAceito() throws IOException {
        demanda(StatusDemanda.EM_ANDAMENTO);
        FotoFiscalizacao r = service.enviar(UsuarioAutenticado.de(gestor), 5L,
                arquivo("sp.jpg", recurso("gps-fora-mt.jpg")));
        assertThat(fotoSalva().isGpsForaMt()).isTrue();
        assertThat(r.conferencia()).isEqualTo(ConferenciaExif.FORA_MT);
        assertThat(r.distanciaKm().doubleValue()).isGreaterThan(1000);
    }

    @Test
    void fotoSemGpsNaoTemDistancia() throws IOException {
        demanda(StatusDemanda.EM_ANDAMENTO);
        FotoFiscalizacao r = service.enviar(UsuarioAutenticado.de(fiscal), 5L, arquivo("a.jpg", recurso("sem-gps.jpg")));
        assertThat(r.exifLatitude()).isNull();
        assertThat(r.distanciaKm()).isNull();
        assertThat(r.exifDataHora()).isNotNull();
        assertThat(r.conferencia()).isEqualTo(ConferenciaExif.SEM_GPS);
    }

    @Test
    void pngEWebpSemExifSaoAceitos() throws IOException {
        demanda(StatusDemanda.EM_ANDAMENTO);
        FotoFiscalizacao png = service.enviar(UsuarioAutenticado.de(fiscal), 5L, arquivo("", recurso("sem-exif.png")));
        assertThat(png.mime()).isEqualTo("image/png");
        assertThat(png.nomeOriginal()).isEqualTo("foto.png");
        FotoFiscalizacao webp = service.enviar(UsuarioAutenticado.de(fiscal), 5L, arquivo("x.webp", recurso("sem-exif.webp")));
        assertThat(webp.mime()).isEqualTo("image/webp");
    }

    @Test
    void arquivoFalsoComExtensaoJpgEhRecusadoPelosMagicBytes() throws IOException {
        demanda(StatusDemanda.EM_ANDAMENTO);
        byte[] pdf = "%PDF-1.7 conteudo ficticio".getBytes();
        assertThatThrownBy(() -> service.enviar(UsuarioAutenticado.de(fiscal), 5L, arquivo("foto.jpg", pdf)))
                .isInstanceOf(ArquivoNaoSuportadoException.class);
        byte[] exe = {'M', 'Z', 0, 0, 0, 0};
        assertThatThrownBy(() -> service.enviar(UsuarioAutenticado.de(fiscal), 5L, arquivo("foto.png", exe)))
                .isInstanceOf(ArquivoNaoSuportadoException.class);
        verify(storage, never()).store(any());
        verify(fotos, never()).save(any());
    }

    @Test
    void fotoAcimaDoLimiteEhRecusada() {
        demanda(StatusDemanda.EM_ANDAMENTO);
        byte[] grande = new byte[(int) FotoFiscalizacaoService.LIMITE_BYTES + 1];
        grande[0] = (byte) 0xFF;
        grande[1] = (byte) 0xD8;
        grande[2] = (byte) 0xFF;
        assertThatThrownBy(() -> service.enviar(UsuarioAutenticado.de(fiscal), 5L, arquivo("g.jpg", grande)))
                .isInstanceOf(ArquivoMuitoGrandeException.class);
    }

    @Test
    void fiscalQueNaoEhODesignadoRecebe403() throws IOException {
        demanda(StatusDemanda.EM_ANDAMENTO);
        byte[] bytes = recurso("gps-cuiaba.jpg");
        assertThatThrownBy(() -> service.enviar(UsuarioAutenticado.de(outroFiscal), 5L, arquivo("a.jpg", bytes)))
                .isInstanceOf(AccessDeniedException.class);
        verify(storage, never()).store(any());
    }

    @Test
    void soEmAndamentoESemPassarDoMaximo() throws IOException {
        byte[] bytes = recurso("gps-cuiaba.jpg");
        demanda(StatusDemanda.DISTRIBUIDA);
        assertThatThrownBy(() -> service.enviar(UsuarioAutenticado.de(fiscal), 5L, arquivo("a.jpg", bytes)))
                .isInstanceOf(ConflitoException.class);
        demanda(StatusDemanda.EM_ANDAMENTO);
        when(fotos.countByDemandaId(5L)).thenReturn((long) FotoFiscalizacaoService.MAX_FOTOS_POR_DEMANDA);
        assertThatThrownBy(() -> service.enviar(UsuarioAutenticado.de(fiscal), 5L, arquivo("a.jpg", bytes)))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void falhaAoGravarNoBancoRemoveOArquivo() throws IOException {
        demanda(StatusDemanda.EM_ANDAMENTO);
        org.mockito.Mockito.doThrow(new IllegalStateException("falha simulada")).when(fotos).save(any());
        byte[] bytes = recurso("sem-gps.jpg");
        assertThatThrownBy(() -> service.enviar(UsuarioAutenticado.de(fiscal), 5L, arquivo("a.jpg", bytes)))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).delete("2026/11/chave-opaca");
    }

    @Test
    void distanciaAteABordaDaAreaOuHaversineAteOPonto() {
        Demanda d = new Demanda("DEM-2026/0002", gestor);
        assertThat(FotoFiscalizacaoService.distanciaKm(d, -15.6, -56.1)).isNull();
        d.setLatitude(new BigDecimal("-15.601"));
        d.setLongitude(new BigDecimal("-56.097"));
        // Cuiabá → Várzea Grande (~7 km), por Haversine até a coordenada.
        assertThat(FotoFiscalizacaoService.distanciaKm(d, -15.6467, -56.1325)).isBetween(5.0, 7.5);
        ReflectionTestUtils.setField(d, "areaGeometria", AREA_CUIABA);
        assertThat(FotoFiscalizacaoService.distanciaKm(d, -15.60, -56.10)).isZero(); // dentro da área
        // ~1 km a leste da borda (-56.09): 0,01° de longitude em -15,6° ≈ 1,07 km.
        assertThat(FotoFiscalizacaoService.distanciaKm(d, -15.60, -56.08)).isBetween(1.0, 1.15);
    }

    @Test
    void exclusaoSoPorQuemEnviouEAntesDaConclusao() throws IOException {
        Demanda d = demanda(StatusDemanda.AGUARDANDO_CONCLUSAO);
        FiscalizacaoFoto foto = new FiscalizacaoFoto(d, "2026/11/k", "a".repeat(64), "image/jpeg", 10, "f.jpg",
                null, null, null, null, false, fiscal);
        ReflectionTestUtils.setField(foto, "id", 7L);
        when(fotos.buscarDaDemanda(5L, 7L)).thenReturn(Optional.of(foto));

        assertThatThrownBy(() -> service.excluir(UsuarioAutenticado.de(gestor), 5L, 7L))
                .isInstanceOf(AccessDeniedException.class);
        service.excluir(UsuarioAutenticado.de(fiscal), 5L, 7L);
        verify(fotos).delete(foto);
        verify(storage, never()).store(any());

        d.setStatus(StatusDemanda.FINALIZADA);
        assertThatThrownBy(() -> service.excluir(UsuarioAutenticado.de(fiscal), 5L, 7L))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void magicBytesReconhecemSoJpegPngWebp() throws IOException {
        assertThat(TipoImagem.detectar(recurso("gps-cuiaba.jpg"))).contains(TipoImagem.JPEG);
        assertThat(TipoImagem.detectar(recurso("sem-exif.png"))).contains(TipoImagem.PNG);
        assertThat(TipoImagem.detectar(recurso("sem-exif.webp"))).contains(TipoImagem.WEBP);
        assertThat(TipoImagem.detectar("GIF89a".getBytes())).isEmpty();
        assertThat(TipoImagem.detectar("RIFF0000WAVE".getBytes())).isEmpty();
        assertThat(TipoImagem.detectar(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(TipoImagem.detectar(new byte[0])).isEmpty();
        assertThat(TipoImagem.detectar(null)).isEmpty();
    }

    @Test
    void pngBombaZtxtNaoEDescompactado() throws IOException {
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        byte[] ihdr = {0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0};
        chunkPng(png, "IHDR", ihdr);
        java.io.ByteArrayOutputStream z = new java.io.ByteArrayOutputStream();
        z.write("Comment".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        z.write(0);
        z.write(0);
        java.util.zip.DeflaterOutputStream d = new java.util.zip.DeflaterOutputStream(z,
                new java.util.zip.Deflater(9));
        byte[] zeros = new byte[1 << 20];
        for (int i = 0; i < 600; i++) { // ~600 MB descompactados: estouraria o heap se alguém lesse
            d.write(zeros);
        }
        d.close();
        chunkPng(png, "zTXt", z.toByteArray());
        chunkPng(png, "IEND", new byte[0]);

        long ini = System.nanoTime();
        LeitorExif.Exif exif = LeitorExif.ler(png.toByteArray());

        assertThat(exif).isEqualTo(LeitorExif.Exif.VAZIO);
        assertThat((System.nanoTime() - ini) / 1_000_000).isLessThan(2000L);
    }

    @Test
    void webpBombaNaoAlocaChunkGigante() throws IOException {
        byte[] bomba256Mb = new byte[] {
            'R', 'I', 'F', 'F', (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F,
            'W', 'E', 'B', 'P',
            'E', 'X', 'I', 'F', 0x00, 0x00, 0x00, 0x10
        };
        byte[] bomba2Gb = new byte[] {
            'R', 'I', 'F', 'F', (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            'W', 'E', 'B', 'P',
            'V', 'P', '8', ' ', 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
            'E', 'X', 'I', 'F', (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F
        };

        long ini = System.nanoTime();
        assertThat(LeitorExif.ler(bomba256Mb)).isEqualTo(LeitorExif.Exif.VAZIO);
        assertThat(LeitorExif.ler(bomba2Gb)).isEqualTo(LeitorExif.Exif.VAZIO);
        assertThat((System.nanoTime() - ini) / 1_000_000).isLessThan(1000L);

        // WebP válido com chunk VP8 ímpar (padding de 1 byte) seguido de chunk EXIF real (reaproveitando APP1 do JPEG).
        byte[] jpg = recurso("gps-cuiaba.jpg");
        int app1Inicio = -1;
        int app1Tam = 0;
        for (int i = 2; i < jpg.length - 8; i++) {
            if ((jpg[i] & 0xFF) == 0xFF && (jpg[i + 1] & 0xFF) == 0xE1) {
                app1Tam = ((jpg[i + 2] & 0xFF) << 8) | (jpg[i + 3] & 0xFF);
                app1Inicio = i + 4;
                app1Tam -= 2;
                break;
            }
        }
        assertThat(app1Inicio).isGreaterThan(0);
        java.io.ByteArrayOutputStream webp = new java.io.ByteArrayOutputStream();
        webp.write("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        webp.write(new byte[] {0, 0, 0, 0}); // tamanho RIFF ignorado pelo leitor seguro
        webp.write("WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        webp.write("VP8 ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        webp.write(new byte[] {3, 0, 0, 0, 1, 2, 3, 0}); // 3 bytes + 1 byte padding par RIFF
        webp.write("EXIF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        webp.write(new byte[] {
            (byte) (app1Tam & 0xFF), (byte) ((app1Tam >> 8) & 0xFF),
            (byte) ((app1Tam >> 16) & 0xFF), (byte) ((app1Tam >> 24) & 0xFF)
        });
        webp.write(jpg, app1Inicio, app1Tam);
        LeitorExif.Exif extraido = LeitorExif.ler(webp.toByteArray());
        assertThat(extraido.temGps()).isTrue();
        assertThat(extraido.latitude()).isCloseTo(-15.601, org.assertj.core.data.Offset.offset(0.005));
    }

    private static void chunkPng(java.io.ByteArrayOutputStream out, String tipo, byte[] dados) throws IOException {
        java.io.DataOutputStream o = new java.io.DataOutputStream(out);
        o.writeInt(dados.length);
        byte[] t = tipo.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        o.write(t);
        o.write(dados);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(t);
        crc.update(dados);
        o.writeInt((int) crc.getValue());
    }

    @Test
    void leitorExifToleraConteudoCorrompido() throws IOException {
        byte[] truncado = java.util.Arrays.copyOf(recurso("gps-cuiaba.jpg"), 40);
        assertThat(LeitorExif.ler(truncado).temGps()).isFalse();
        assertThat(LeitorExif.ler(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3})).isNotNull();
        LeitorExif.Exif exif = LeitorExif.ler(recurso("gps-fora-mt.jpg"));
        assertThat(exif.latitude()).isCloseTo(-23.5505, org.assertj.core.data.Offset.offset(0.0005));
        assertThat(FotoFiscalizacaoService.dentroDeMt(exif.latitude(), exif.longitude())).isFalse();
        assertThat(FotoFiscalizacaoService.dentroDeMt(-15.6, -56.1)).isTrue();
    }
}
