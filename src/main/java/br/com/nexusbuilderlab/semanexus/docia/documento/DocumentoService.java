package br.com.nexusbuilderlab.semanexus.docia.documento;

import br.com.nexusbuilderlab.semanexus.common.dto.DemandaRef;
import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.core.notificacao.NotificacaoService;
import br.com.nexusbuilderlab.semanexus.core.notificacao.TipoNotificacao;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoDetalhe;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoFiltro;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoResumo;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoUploadRequest;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.PreAnaliseDocumento;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Upload e download de documentos do DocIA: validação, volume, metadados/texto no Oracle e auditoria. */
@Service
public class DocumentoService {

    static final String ENTIDADE = "DOCUMENTO";
    static final int TRECHO_MAX = 2000;
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
    private final NotificacaoService notificacoes;
    private final ApplicationEventPublisher eventos;

    public DocumentoService(DocumentoRepository documentos, MunicipioRepository municipios, UsuarioRepository usuarios,
                            StorageService storage, TextExtractor extrator, AuditService auditoria,
                            DemandaService demandas, JdbcTemplate jdbc, NotificacaoService notificacoes,
                            ApplicationEventPublisher eventos) {
        this.documentos = documentos;
        this.municipios = municipios;
        this.usuarios = usuarios;
        this.storage = storage;
        this.extrator = extrator;
        this.auditoria = auditoria;
        this.demandas = demandas;
        this.jdbc = jdbc;
        this.notificacoes = notificacoes;
        this.eventos = eventos;
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
            // Busca lexical (#164): grava o texto normalizado na coluna derivada TEXTO_BUSCA (mesma transação).
            IndiceBuscaDocumentos.atualizar(jdbc, documento.getId(), documento.getTextoExtraido());
            if (pedido.demandaId() != null) {
                jdbc.update("INSERT INTO DOCUMENTO_DEMANDA (DOCUMENTO_ID, DEMANDA_ID, VINCULADO_POR) VALUES (?, ?, ?)",
                        documento.getId(), pedido.demandaId(), ator.id());
            }
            // Busca híbrida (#141): indexa os trechos depois do commit, em segundo plano.
            if (documento.getId() != null) {
                eventos.publishEvent(new DocumentoTextoAlterado(documento.getId()));
            }
            auditoria.registrar("UPLOAD", ENTIDADE, String.valueOf(documento.getId()),
                    "Upload de documento (%s, %d bytes)".formatted(tipo.mime(), bytes.length));
            return detalhe(documento);
        } catch (RuntimeException e) {
            removerSilencioso(chave);
            throw e;
        }
    }

    /** Sugere metadados a partir do arquivo sem gravar nada (nem arquivo, nem banco). */
    /** Sem transação: o OCR pode demorar e não deve segurar conexão; só a consulta de municípios é curta. */
    public PreAnaliseDocumento preAnalisar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.getSize() > LIMITE_BYTES) {
            throw new ArquivoMuitoGrandeException("O arquivo excede o limite de 10 MB.");
        }
        String nome = sanearNome(arquivo.getOriginalFilename());
        TipoArquivo tipo = TipoArquivo.porNome(nome).orElseThrow(() -> new ArquivoNaoSuportadoException(
                "Extensão não permitida. Use pdf, txt, md, csv ou docx."));
        byte[] bytes = ler(arquivo);
        if (bytes.length == 0 || !tipo.conteudoValido(bytes)) {
            throw new ArquivoNaoSuportadoException("O conteúdo do arquivo não corresponde à extensão informada.");
        }
        TextExtractor.Extracao e = extrator.extrairCompleto(tipo, bytes);
        return PreAnaliseRegras.analisar(nome, e.texto(), e.avisos(), municipios.findAllByOrderByNomeAsc());
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

    // ------------------------------------------------------------------ biblioteca

    @Transactional(readOnly = true)
    public PageResponse<DocumentoResumo> listar(DocumentoFiltro filtro, Pageable paginacao) {
        if (filtro.de() != null && filtro.ate() != null && filtro.de().isAfter(filtro.ate())) {
            throw new DadosInvalidosException("de", "A data inicial não pode ser posterior à data final.");
        }
        Sort ordenacao = ordenacao(paginacao.getSort());
        List<Long> ids = null;
        if (filtro.demandaId() != null) {
            ids = jdbc.queryForList("SELECT DOCUMENTO_ID FROM DOCUMENTO_DEMANDA WHERE DEMANDA_ID = ?", Long.class,
                    filtro.demandaId());
        }
        if (filtro.q() != null && !filtro.q().isBlank()) {
            String padrao = DocumentoSpecification.padraoBusca(filtro.q());
            List<Long> achados = jdbc.queryForList(DocumentoSpecification.SQL_BUSCA, Long.class,
                    padrao, padrao, padrao, padrao);
            if (ids == null) {
                ids = achados;
            } else {
                ids.retainAll(achados);
            }
        }
        Page<Documento> pagina = documentos.findAll(DocumentoSpecification.listagem(filtro, ids, ordenacao),
                PageRequest.of(paginacao.getPageNumber(), paginacao.getPageSize()));
        return PageResponse.of(pagina, DocumentoService::resumo);
    }

    @Transactional(readOnly = true)
    public DocumentoDetalhe detalhar(Long id) {
        return detalhe(ativo(id));
    }

    /** Metadados com SHA-256 de um documento vinculado à demanda (usado na geração do dossiê em PDF, Issue #146). */
    public record DocumentoVinculado(Long id, String titulo, String categoria, String tipo,
                                     java.time.LocalDate dataDocumento, String nomeArquivo, String mime,
                                     long tamanho, String sha256, UsuarioRef responsavel,
                                     java.time.LocalDateTime criadoEm) {
    }

    /** Documentos vinculados à demanda (404/403 conforme a demanda e o escopo do fiscal). */
    @Transactional(readOnly = true)
    public List<DocumentoResumo> listarDaDemanda(UsuarioAutenticado ator, Long demandaId) {
        demandas.detalhar(ator, demandaId);
        return listar(new DocumentoFiltro(null, null, null, null, demandaId, null, null),
                PageRequest.of(0, 100, Sort.by(Sort.Order.desc("criadoEm")))).content();
    }

    /** Documentos vinculados à demanda com SHA-256 para composição do dossiê (404/403 conforme escopo). */
    @Transactional(readOnly = true)
    public List<DocumentoVinculado> listarVinculadosParaDossie(UsuarioAutenticado ator, Long demandaId) {
        demandas.detalhar(ator, demandaId);
        List<Long> ids = jdbc.queryForList("SELECT DOCUMENTO_ID FROM DOCUMENTO_DEMANDA WHERE DEMANDA_ID = ?",
                Long.class, demandaId);
        if (ids.isEmpty()) {
            return List.of();
        }
        var filtro = new DocumentoFiltro(null, null, null, null, demandaId, null, null);
        return documentos.findAll(
                        DocumentoSpecification.listagem(filtro, ids, Sort.by(Sort.Order.desc("criadoEm"))),
                        PageRequest.of(0, 100))
                .stream()
                .map(d -> new DocumentoVinculado(d.getId(), d.getTitulo(), d.getCategoria(), d.getTipo(),
                        d.getDataDocumento(), d.getNomeArquivo(), d.getMime(), d.getTamanho(), d.getSha256(),
                        UsuarioRef.de(d.getResponsavel()), d.getCriadoEm()))
                .toList();
    }

    @Transactional
    public DocumentoResumo vincular(UsuarioAutenticado ator, Long demandaId, Long documentoId) {
        var demanda = demandas.detalhar(ator, demandaId);
        Documento d = ativo(documentoId);
        Integer existentes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM DOCUMENTO_DEMANDA WHERE DOCUMENTO_ID = ? AND DEMANDA_ID = ?", Integer.class,
                documentoId, demandaId);
        if (existentes != null && existentes > 0) {
            throw new ConflitoException("O documento já está vinculado a esta demanda.");
        }
        try {
            jdbc.update("INSERT INTO DOCUMENTO_DEMANDA (DOCUMENTO_ID, DEMANDA_ID, VINCULADO_POR) VALUES (?, ?, ?)",
                    documentoId, demandaId, ator.id());
        } catch (DuplicateKeyException e) {
            throw new ConflitoException("O documento já está vinculado a esta demanda.");
        }
        demandas.registrarEventoDocumento(ator, demandaId,
                "Documento vinculado: " + d.getTitulo() + " (#" + documentoId + ").");
        if (demanda.fiscal() != null && !demanda.fiscal().id().equals(ator.id())) {
            notificacoes.notificar(demanda.fiscal().id(), TipoNotificacao.DOCUMENTO_VINCULADO,
                    "Novo documento na demanda " + demanda.numero(),
                    "O documento \"" + d.getTitulo() + "\" foi vinculado à demanda " + demanda.numero() + ".",
                    demandaId, "DOCUMENTO:" + demandaId + ":" + documentoId + ":" + System.currentTimeMillis());
        }
        auditoria.registrar("VINCULAR", ENTIDADE, String.valueOf(documentoId), "Vinculado à demanda " + demandaId);
        return resumo(d);
    }

    @Transactional
    public void desvincular(UsuarioAutenticado ator, Long demandaId, Long documentoId) {
        demandas.detalhar(ator, demandaId);
        Documento d = ativo(documentoId);
        int removidos = jdbc.update("DELETE FROM DOCUMENTO_DEMANDA WHERE DOCUMENTO_ID = ? AND DEMANDA_ID = ?",
                documentoId, demandaId);
        if (removidos == 0) {
            throw RecursoNaoEncontradoException.de("Vínculo do documento com a demanda", documentoId);
        }
        demandas.registrarEventoDocumento(ator, demandaId,
                "Documento desvinculado: " + d.getTitulo() + " (#" + documentoId + ").");
        auditoria.registrar("DESVINCULAR", ENTIDADE, String.valueOf(documentoId),
                "Desvinculado da demanda " + demandaId);
    }

    private Documento ativo(Long id) {
        return documentos.buscarAtivo(id).orElseThrow(() -> RecursoNaoEncontradoException.de("Documento", id));
    }

    private static Sort ordenacao(Sort pedida) {
        if (pedida.isUnsorted()) {
            return Sort.by(Sort.Order.desc("criadoEm"));
        }
        List<String> invalidos = pedida.stream().map(Sort.Order::getProperty)
                .filter(p -> !DocumentoSpecification.CAMPOS_ORDENAVEIS.contains(p)).toList();
        if (!invalidos.isEmpty()) {
            throw new DadosInvalidosException("sort", "Campo de ordenação inválido: " + invalidos.get(0)
                    + ". Use: " + DocumentoSpecification.CAMPOS_ORDENAVEIS.stream().sorted()
                    .collect(Collectors.joining(", ")) + ".");
        }
        return pedida;
    }

    static DocumentoResumo resumo(Documento d) {
        Municipio m = d.getMunicipio();
        return new DocumentoResumo(d.getId(), d.getTitulo(), d.getCategoria(), d.getTipo(), d.getDataDocumento(),
                m == null ? null : new MunicipioRef(m.getId(), m.getNome(), m.getBioma()), listaPalavras(d),
                d.getNomeArquivo(), d.getMime(), d.getTamanho(), UsuarioRef.de(d.getResponsavel()), d.getCriadoEm());
    }

    private static List<String> listaPalavras(Documento d) {
        return d.getPalavrasChave() == null ? List.of() : Arrays.asList(d.getPalavrasChave().split(","));
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
        List<String> palavras = listaPalavras(d);
        String texto = d.getTextoExtraido();
        String trecho = texto == null || texto.length() <= TRECHO_MAX ? texto : texto.substring(0, TRECHO_MAX);
        return new DocumentoDetalhe(d.getId(), d.getTitulo(), d.getDescricao(), d.getCategoria(), d.getTipo(),
                d.getDataDocumento(), m == null ? null : new MunicipioRef(m.getId(), m.getNome(), m.getBioma()),
                palavras, d.getNomeArquivo(), d.getMime(), d.getTamanho(), d.getSha256(), trecho, refs,
                UsuarioRef.de(d.getResponsavel()), d.getCriadoEm(), d.getAtualizadoEm(),
                auditoria.historicoDaEntidade(ENTIDADE, String.valueOf(d.getId())));
    }
}
