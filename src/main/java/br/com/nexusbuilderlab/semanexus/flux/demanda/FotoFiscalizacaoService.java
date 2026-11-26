package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.common.geo.GeoJson;
import br.com.nexusbuilderlab.semanexus.common.geo.Geodesia;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.ArquivoMuitoGrandeException;
import br.com.nexusbuilderlab.semanexus.docia.storage.StorageService;
import br.com.nexusbuilderlab.semanexus.docia.storage.TipoImagem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.LeitorExif.Exif;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FotoFiscalizacao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FotoFiscalizacao.ConferenciaExif;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteLoader;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Fotos de evidência da fiscalização (Issue #145). Envio só na demanda {@code EM_ANDAMENTO}, pelo fiscal designado
 * (ou GESTOR/ADMIN) — as mesmas regras do registro da fiscalização. JPEG/PNG/WebP reconhecidos pelos magic bytes no
 * {@link StorageService}; arquivo no volume (nunca no banco) com SHA-256; EXIF (data/hora, GPS) lido com
 * {@link LeitorExif} só como informação: o arquivo é gravado intacto e o EXIF nunca decide autorização.
 */
@Service
public class FotoFiscalizacaoService {

    /** Limite por foto (igual ao do multipart da aplicação). */
    static final long LIMITE_BYTES = 10L * 1024 * 1024;
    /** Máximo de fotos por demanda (proteção do volume na demonstração). */
    static final int MAX_FOTOS_POR_DEMANDA = 20;
    /** Distância até a qual o GPS do EXIF "confere" com o local da demanda. */
    static final double LIMITE_CONFERE_KM = 1.0;

    private static final double LAT_MIN = ReferenciaSateliteLoader.LAT_MIN.doubleValue();
    private static final double LAT_MAX = ReferenciaSateliteLoader.LAT_MAX.doubleValue();
    private static final double LON_MIN = ReferenciaSateliteLoader.LON_MIN.doubleValue();
    private static final double LON_MAX = ReferenciaSateliteLoader.LON_MAX.doubleValue();

    private final DemandaRepository demandas;
    private final DemandaHistoricoRepository historicos;
    private final FiscalizacaoFotoRepository fotos;
    private final UsuarioRepository usuarios;
    private final DemandaService demandaService;
    private final StorageService storage;
    private final AuditService auditoria;

    public FotoFiscalizacaoService(DemandaRepository demandas, DemandaHistoricoRepository historicos,
                                   FiscalizacaoFotoRepository fotos, UsuarioRepository usuarios,
                                   DemandaService demandaService, StorageService storage, AuditService auditoria) {
        this.demandas = demandas;
        this.historicos = historicos;
        this.fotos = fotos;
        this.usuarios = usuarios;
        this.demandaService = demandaService;
        this.storage = storage;
        this.auditoria = auditoria;
    }

    /** Conteúdo da foto para download (nome já saneado). */
    public record FotoBaixada(String nome, String mime, byte[] conteudo) {
    }

    @Transactional
    public FotoFiscalizacao enviar(UsuarioAutenticado ator, Long demandaId, MultipartFile arquivo) {
        Demanda demanda = demandas.buscarParaEscrita(demandaId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Demanda", demandaId));
        ExecucaoSuporte.exigirFiscalDesignado(ator, demanda);
        if (demanda.getStatus() != StatusDemanda.EM_ANDAMENTO) {
            throw new ConflitoException("Fotos de evidência só podem ser enviadas com a demanda em andamento.");
        }
        if (fotos.countByDemandaId(demandaId) >= MAX_FOTOS_POR_DEMANDA) {
            throw new ConflitoException("A demanda já tem o máximo de " + MAX_FOTOS_POR_DEMANDA + " fotos.");
        }
        if (arquivo == null || arquivo.isEmpty()) {
            throw new DadosInvalidosException("arquivo",
                    "Envie a foto na parte `arquivo`.");
        }
        if (arquivo.getSize() > LIMITE_BYTES) {
            throw new ArquivoMuitoGrandeException("A foto excede o limite de 10 MB.");
        }
        byte[] bytes = ler(arquivo);
        if (bytes.length > LIMITE_BYTES) {
            throw new ArquivoMuitoGrandeException("A foto excede o limite de 10 MB.");
        }
        TipoImagem tipo = storage.validarImagem(bytes); // 415 se não for JPEG/PNG/WebP pelos magic bytes
        String nome = sanearNome(arquivo.getOriginalFilename(), tipo);
        Exif exif = LeitorExif.ler(bytes);
        Double distancia = exif.temGps() ? distanciaKm(demanda, exif.latitude(), exif.longitude()) : null;
        boolean foraMt = exif.temGps() && !dentroDeMt(exif.latitude(), exif.longitude());
        Usuario autor = usuarios.findById(ator.id())
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuário", ator.id()));

        String chave;
        try {
            chave = storage.store(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar a foto no armazenamento.", e);
        }
        try {
            FiscalizacaoFoto foto = fotos.save(new FiscalizacaoFoto(demanda, chave, sha256(bytes), tipo.mime(),
                    bytes.length, nome, exif.dataHora(), coordenada(exif.latitude()), coordenada(exif.longitude()),
                    distancia == null ? null : BigDecimal.valueOf(distancia).setScale(3, RoundingMode.HALF_UP),
                    foraMt, autor));
            historicos.save(ExecucaoSuporte.evento(demanda, TipoHistorico.ATIVIDADE, StatusDemanda.EM_ANDAMENTO,
                    StatusDemanda.EM_ANDAMENTO, "[Foto] Evidência anexada: " + nome + resumoExif(foto), autor));
            auditoria.registrar("UPLOAD_FOTO_FISCALIZACAO", DemandaService.ENTIDADE, String.valueOf(demandaId),
                    "Demanda %s: foto de evidência anexada (%s, %d bytes, sha256 %s…)%s".formatted(
                            demanda.getNumero(), tipo.mime(), bytes.length, foto.getSha256().substring(0, 12),
                            foraMt ? "; GPS do EXIF fora de MT" : ""));
            return dto(foto, demanda);
        } catch (RuntimeException e) {
            removerSilencioso(chave);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<FotoFiscalizacao> listar(UsuarioAutenticado ator, Long demandaId) {
        demandaService.detalhar(ator, demandaId); // 404 se não existe; 403 fora do escopo do fiscal
        Demanda demanda = demandas.getReferenceById(demandaId);
        return fotos.listarPorDemanda(demandaId).stream().map(f -> dto(f, demanda)).toList();
    }

    @Transactional(readOnly = true)
    public FotoBaixada baixar(UsuarioAutenticado ator, Long demandaId, Long fotoId) {
        demandaService.detalhar(ator, demandaId);
        FiscalizacaoFoto foto = fotos.buscarDaDemanda(demandaId, fotoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Foto", fotoId));
        try {
            return new FotoBaixada(foto.getNomeOriginal(), foto.getMime(), storage.load(foto.getChaveArquivo()));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler a foto do armazenamento.", e);
        }
    }

    /** Só quem enviou exclui, e só antes da conclusão da demanda. O arquivo sai do volume após o commit. */
    @Transactional
    public void excluir(UsuarioAutenticado ator, Long demandaId, Long fotoId) {
        Demanda demanda = demandas.buscarParaEscrita(demandaId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Demanda", demandaId));
        demandaService.detalhar(ator, demandaId);
        FiscalizacaoFoto foto = fotos.buscarDaDemanda(demandaId, fotoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Foto", fotoId));
        if (!Objects.equals(foto.getAutor().getId(), ator.id())) {
            throw new AccessDeniedException("Somente quem enviou a foto pode excluí-la.");
        }
        if (demanda.getStatus() == StatusDemanda.FINALIZADA) {
            throw new ConflitoException("A demanda já foi concluída; a foto não pode mais ser excluída.");
        }
        Usuario autor = foto.getAutor();
        String chave = foto.getChaveArquivo();
        fotos.delete(foto);
        historicos.save(ExecucaoSuporte.evento(demanda, TipoHistorico.ATIVIDADE, demanda.getStatus(),
                demanda.getStatus(), "[Foto] Evidência removida: " + foto.getNomeOriginal(), autor));
        auditoria.registrar("EXCLUIR_FOTO_FISCALIZACAO", DemandaService.ENTIDADE, String.valueOf(demandaId),
                "Demanda %s: foto de evidência %d removida (sha256 %s…)".formatted(demanda.getNumero(), fotoId,
                        foto.getSha256().substring(0, 12)));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    removerSilencioso(chave);
                }
            });
        } else {
            removerSilencioso(chave);
        }
    }

    // ------------------------------------------------------------------ regras

    /**
     * Distância em km do GPS até a demanda: até a borda do polígono (0 se dentro) quando há área; senão Haversine até
     * a coordenada; {@code null} se a demanda não tem referência espacial.
     */
    static Double distanciaKm(Demanda demanda, double lat, double lon) {
        if (demanda.getAreaGeometria() != null) {
            Map<String, Object> geometria = GeoJson.geometria(demanda.getAreaGeometria());
            double d = Geodesia.distanciaAoPoligonoKm(lat, lon, geometria);
            if (!Double.isNaN(d)) {
                return d;
            }
        }
        if (demanda.getLatitude() != null && demanda.getLongitude() != null) {
            return Geodesia.haversineKm(lat, lon, demanda.getLatitude().doubleValue(),
                    demanda.getLongitude().doubleValue());
        }
        return null;
    }

    static boolean dentroDeMt(double lat, double lon) {
        return lat >= LAT_MIN && lat <= LAT_MAX && lon >= LON_MIN && lon <= LON_MAX;
    }

    static ConferenciaExif conferencia(FiscalizacaoFoto f) {
        if (f.getExifLatitude() == null) {
            return ConferenciaExif.SEM_GPS;
        }
        if (f.isGpsForaMt()) {
            return ConferenciaExif.FORA_MT;
        }
        if (f.getDistanciaKm() == null) {
            return ConferenciaExif.SEM_REFERENCIA;
        }
        return f.getDistanciaKm().doubleValue() <= LIMITE_CONFERE_KM ? ConferenciaExif.CONFERE
                : ConferenciaExif.DIVERGENTE;
    }

    /** Remove caminho, controles e aspas; vazio vira {@code foto.<ext>}. O nome nunca vai para o disco. */
    static String sanearNome(String original, TipoImagem tipo) {
        String nome = original == null ? "" : original;
        nome = nome.substring(Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\')) + 1);
        nome = nome.replaceAll("[\\p{Cntrl}\"<>]", "").strip();
        if (nome.isEmpty() || nome.chars().allMatch(c -> c == '.')) {
            nome = "foto." + tipo.extensao();
        }
        if (nome.length() > 255) {
            int ponto = nome.lastIndexOf('.');
            String ext = ponto >= 0 && nome.length() - ponto <= 10 ? nome.substring(ponto) : "";
            nome = nome.substring(0, 255 - ext.length()) + ext;
        }
        return nome;
    }

    private static FotoFiscalizacao dto(FiscalizacaoFoto f, Demanda demanda) {
        return new FotoFiscalizacao(f.getId(), demanda.getId(), f.getNomeOriginal(), f.getMime(), f.getTamanho(),
                f.getSha256(), f.getExifDataHora(), f.getExifLatitude(), f.getExifLongitude(), f.getDistanciaKm(),
                f.isGpsForaMt(), conferencia(f), UsuarioRef.de(f.getAutor()), f.getCriadoEm());
    }

    private static String resumoExif(FiscalizacaoFoto f) {
        ConferenciaExif c = conferencia(f);
        return switch (c) {
            case SEM_GPS -> " (EXIF sem GPS)";
            case FORA_MT -> " (GPS do EXIF fora de MT)";
            case SEM_REFERENCIA -> " (demanda sem coordenada de referência)";
            default -> " (GPS a " + f.getDistanciaKm().toPlainString() + " km da demanda)";
        };
    }

    private static BigDecimal coordenada(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP);
    }

    private static byte[] ler(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler a foto enviada.", e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void removerSilencioso(String chave) {
        try {
            storage.delete(chave);
        } catch (IOException | RuntimeException ignorado) {
            // melhor esforço: arquivo órfão no volume não afeta a consistência do banco
        }
    }
}
