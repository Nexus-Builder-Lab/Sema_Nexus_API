package br.com.nexusbuilderlab.semanexus.docia.documento;

import br.com.nexusbuilderlab.semanexus.common.dto.DemandaRef;
import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoDetalhe;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoUploadRequest;
import br.com.nexusbuilderlab.semanexus.docia.storage.StorageService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Upload e download de documentos do DocIA: validação, volume, metadados/texto no Oracle e auditoria. */
@Service
public class DocumentoService {

    static final String ENTIDADE = "DOCUMENTO";
    static final long LIMITE_BYTES = 10L * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

    private final DocumentoRepository documentos;
    private final MunicipioRepository municipios;
    private final UsuarioRepository usuarios;
    private final StorageService storage;
    private final TextExtractor extrator;
    private final AuditService auditoria;
    private final DemandaService demandas;
    private final JdbcTemplate jdbc;

    public DocumentoService(DocumentoRepository documentos, MunicipioRepository municipios, UsuarioRepository usuarios,
                            StorageService storage, TextExtractor extrator, AuditService auditoria,
                            DemandaService demandas, JdbcTemplate jdbc) {
        this.documentos = documentos;
        this.municipios = municipios;
        this.usuarios = usuarios;
        this.storage = storage;
        this.extrator = extrator;
        this.auditoria = auditoria;
        this.demandas = demandas;
        this.jdbc = jdbc;
    }

    /** Arquivo pronto para download (nome original já saneado). */
    public record ArquivoBaixado(String nome, String mime, byte[] conteudo) {
    }

    @Transactional
    public DocumentoDetalhe enviar(UsuarioAutenticado ator, DocumentoUploadRequest pedido) {
        MultipartFile arquivo = pedido.arquivo();
        if (arquivo.getSize() > LIMITE_BYTES) {
            throw new ArquivoMuitoGrandeException("O arquivo excede o limite de 10 MB.");
        }
        String nome = sanearNome(arquivo.getOriginalFilename());
        TipoArquivo tipo = TipoArquivo.porNome(nome).orElseThrow(() -> new ArquivoNaoSuportadoException(
                "Extensão não permitida. Use pdf, txt, md, csv ou docx."));
        byte[] bytes = ler(arquivo);
        if (bytes.length > LIMITE_BYTES) {
            throw new ArquivoMuitoGrandeException("O arquivo excede o limite de 10 MB.");
        }
        if (bytes.length == 0 || !tipo.conteudoValido(bytes)) {
            throw new ArquivoNaoSuportadoException("O conteúdo do arquivo não corresponde à extensão informada.");
        }
        Municipio municipio = municipio(pedido.municipioId());
        if (pedido.demandaId() != null) {
            demandas.detalhar(ator, pedido.demandaId()); // 404 se não existe; 403 se fora do escopo do fiscal
        }

        String chave;
        try {
            chave = storage.store(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar o arquivo no armazenamento.", e);
        }
        try {
            Documento documento = new Documento(pedido.titulo().strip(), vazioParaNulo(pedido.descricao()),
                    pedido.categoria().strip(), pedido.tipo().strip(), pedido.dataDocumento(), municipio,
                    palavras(pedido.palavrasChave()), nome, tipo.mime(), bytes.length, chave, sha256(bytes),
                    extrator.extrair(tipo, bytes),
                    usuarios.findById(ator.id()).orElseThrow(() -> RecursoNaoEncontradoException.de("Usuário", ator.id())));
            documentos.save(documento);
            if (pedido.demandaId() != null) {
                jdbc.update("INSERT INTO DOCUMENTO_DEMANDA (DOCUMENTO_ID, DEMANDA_ID, VINCULADO_POR) VALUES (?, ?, ?)",
                        documento.getId(), pedido.demandaId(), ator.id());
            }
            auditoria.registrar("UPLOAD", ENTIDADE, String.valueOf(documento.getId()),
                    "Upload de documento (%s, %d bytes)".formatted(tipo.mime(), bytes.length));
            return detalhe(documento);
        } catch (RuntimeException e) {
            removerSilencioso(chave);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public ArquivoBaixado baixar(Long id) {
        Documento d = documentos.buscarAtivo(id).orElseThrow(() -> RecursoNaoEncontradoException.de("Documento", id));
        try {
            return new ArquivoBaixado(d.getNomeArquivo(), d.getMime(), storage.load(d.getStorageKey()));
        } catch (IOException e) {
            throw RecursoNaoEncontradoException.de("Arquivo do documento", id);
        }
    }

    // ------------------------------------------------------------------ apoio

    /** Remove qualquer caminho e caracteres de controle; o nome original só vai para o banco, nunca para o disco. */
    static String sanearNome(String original) {
        String nome = original == null ? "" : original;
        nome = nome.substring(Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\')) + 1);
        nome = nome.replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (nome.length() > 255) {
            int ponto = nome.lastIndexOf('.');
            String ext = ponto >= 0 && nome.length() - ponto <= 10 ? nome.substring(ponto) : "";
            nome = nome.substring(0, 255 - ext.length()) + ext;
        }
        return nome;
    }

    private Municipio municipio(Integer id) {
        if (id == null) {
            return null;
        }
        return municipios.findById(id).orElseThrow(() -> new DadosInvalidosException(
                "municipioId", "Município inexistente ou fora de Mato Grosso."));
    }

    private static byte[] ler(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String palavras(List<String> lista) {
        if (lista == null) {
            return null;
        }
        String junto = lista.stream().filter(Objects::nonNull).map(String::strip).filter(p -> !p.isEmpty())
                .map(p -> p.replace(",", " ")).distinct().collect(Collectors.joining(","));
        return junto.isEmpty() ? null : junto;
    }

    private void removerSilencioso(String chave) {
        try {
            storage.delete(chave);
        } catch (IOException | RuntimeException e) {
            log.warn("Não foi possível remover o arquivo órfão {}", chave);
        }
    }

    private DocumentoDetalhe detalhe(Documento d) {
        List<DemandaRef> refs = jdbc.query(
                "SELECT dm.ID, dm.NUMERO, dm.TITULO FROM DOCUMENTO_DEMANDA dd JOIN DEMANDA dm ON dm.ID = dd.DEMANDA_ID "
                        + "WHERE dd.DOCUMENTO_ID = ? AND dm.EXCLUIDO_EM IS NULL ORDER BY dm.ID",
                (rs, n) -> new DemandaRef(rs.getLong(1), rs.getString(2), rs.getString(3)), d.getId());
        Municipio m = d.getMunicipio();
        List<String> palavras = d.getPalavrasChave() == null ? List.of() : Arrays.asList(d.getPalavrasChave().split(","));
        return new DocumentoDetalhe(d.getId(), d.getTitulo(), d.getDescricao(), d.getCategoria(), d.getTipo(),
                d.getDataDocumento(), m == null ? null : new MunicipioRef(m.getId(), m.getNome(), m.getBioma()),
                palavras, d.getNomeArquivo(), d.getMime(), d.getTamanho(), d.getSha256(), d.getTextoExtraido(), refs,
                UsuarioRef.de(d.getResponsavel()), d.getCriadoEm(), d.getAtualizadoEm());
    }
}
