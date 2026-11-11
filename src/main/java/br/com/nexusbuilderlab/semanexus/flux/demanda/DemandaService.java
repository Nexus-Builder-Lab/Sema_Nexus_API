package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.dto.EquipeRef;
import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.GlobalExceptionHandler.ErroCampo;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaFiltro;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaResumo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FiscalizacaoDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.HistoricoItem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio do cadastro e da consulta de demandas do Flux (Issue #11): cadastro com numeração
 * automática, edição (somente PENDENTE/EM_TRIAGEM), listagem filtrada e paginada, detalhes, histórico e exclusão
 * lógica. Toda operação de escrita grava {@code DEMANDA_HISTORICO} e auditoria. Triagem, distribuição e
 * acompanhamento chegam nas Issues #12 e #13.
 */
@Service
public class DemandaService {

    static final String ENTIDADE = "DEMANDA";
    /** "Hoje" para prazos e datas de recebimento é o dia em Mato Grosso, não o do servidor. */
    static final ZoneId FUSO = ZoneId.of("America/Cuiaba");

    private final DemandaRepository demandas;
    private final DemandaHistoricoRepository historicos;
    private final FiscalizacaoRepository fiscalizacoes;
    private final MunicipioRepository municipios;
    private final EquipeRepository equipes;
    private final UsuarioRepository usuarios;
    private final NumeracaoService numeracao;
    private final AuditService auditoria;
    private final Clock clock;

    public DemandaService(DemandaRepository demandas, DemandaHistoricoRepository historicos,
                          FiscalizacaoRepository fiscalizacoes, MunicipioRepository municipios,
                          EquipeRepository equipes, UsuarioRepository usuarios, NumeracaoService numeracao,
                          AuditService auditoria, Clock clock) {
        this.demandas = demandas;
        this.historicos = historicos;
        this.fiscalizacoes = fiscalizacoes;
        this.municipios = municipios;
        this.equipes = equipes;
        this.usuarios = usuarios;
        this.numeracao = numeracao;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ escrita

    /** Cadastra a demanda com status PENDENTE e número {@code DEM-AAAA/NNNN} (lock pessimista na numeração). */
    @Transactional
    public DemandaDetalhe cadastrar(UsuarioAutenticado ator, DemandaRequest pedido) {
        LocalDate hoje = hoje();
        Municipio municipio = validar(pedido, hoje);
        Usuario autor = usuario(ator);

        Demanda demanda = new Demanda(numeracao.proximoNumero(hoje.getYear()), autor);
        demanda.setStatus(StatusDemanda.PENDENTE);
        aplicar(demanda, pedido, municipio);
        demandas.save(demanda);

        registrarHistorico(demanda, TipoHistorico.CADASTRO, null, StatusDemanda.PENDENTE,
                "Demanda cadastrada.", autor);
        auditoria.registrar("CRIAR", ENTIDADE, String.valueOf(demanda.getId()),
                "Demanda " + demanda.getNumero() + " cadastrada.");
        return detalhe(demanda, hoje);
    }

    /** Edita os dados da demanda; só é permitido em PENDENTE ou EM_TRIAGEM (senão 409). */
    @Transactional
    public DemandaDetalhe editar(UsuarioAutenticado ator, Long id, DemandaRequest pedido) {
        LocalDate hoje = hoje();
        Demanda demanda = obterParaEscrita(id);
        if (demanda.getStatus() != StatusDemanda.PENDENTE && demanda.getStatus() != StatusDemanda.EM_TRIAGEM) {
            throw new ConflitoException("A demanda só pode ser editada nos status Pendente ou Em triagem "
                    + "(status atual: " + demanda.getStatus().rotulo() + ").");
        }
        Municipio municipio = validar(pedido, hoje);
        Usuario autor = usuario(ator);

        List<String> alterados = camposAlterados(demanda, pedido);
        aplicar(demanda, pedido, municipio);
        demandas.saveAndFlush(demanda);
        if (!alterados.isEmpty()) {
            String texto = "Campos alterados: " + String.join(", ", alterados) + ".";
            registrarHistorico(demanda, TipoHistorico.EDICAO, demanda.getStatus(), demanda.getStatus(), texto, autor);
            auditoria.registrar("EDITAR", ENTIDADE, String.valueOf(demanda.getId()),
                    "Demanda " + demanda.getNumero() + " editada. " + texto);
        }
        return detalhe(demanda, hoje);
    }

    /** Exclusão lógica; somente em PENDENTE (senão 409). A autorização (ADMIN) é aplicada no controller. */
    @Transactional
    public void excluir(UsuarioAutenticado ator, Long id) {
        Demanda demanda = obterParaEscrita(id);
        if (demanda.getStatus() != StatusDemanda.PENDENTE) {
            throw new ConflitoException("Somente demandas Pendentes podem ser excluídas "
                    + "(status atual: " + demanda.getStatus().rotulo() + ").");
        }
        registrarHistorico(demanda, TipoHistorico.EXCLUSAO, demanda.getStatus(), null,
                "Demanda excluída logicamente.", usuario(ator));
        demanda.excluir(LocalDateTime.now(clock.withZone(FUSO)));
        demandas.save(demanda);
        auditoria.registrar("EXCLUIR", ENTIDADE, String.valueOf(demanda.getId()),
                "Demanda " + demanda.getNumero() + " excluída logicamente.");
    }

    /**
     * Registra no histórico da demanda o vínculo/desvínculo de documento (tipo DOCUMENTO). Respeita o escopo
     * do FISCAL (403) e devolve 404 se a demanda não existe. Usado pelo DocIA.
     */
    @Transactional
    public void registrarEventoDocumento(UsuarioAutenticado ator, Long id, String texto) {
        Demanda demanda = obterParaEscrita(id);
        verificarEscopo(ator, demanda);
        registrarHistorico(demanda, TipoHistorico.DOCUMENTO, demanda.getStatus(), demanda.getStatus(), texto,
                usuario(ator));
    }

    // ------------------------------------------------------------------ leitura

    /** Lista com filtros combináveis; o perfil FISCAL só enxerga as demandas da sua equipe ou atribuídas a ele. */
    @Transactional(readOnly = true)
    public PageResponse<DemandaResumo> listar(UsuarioAutenticado ator, DemandaFiltro filtro, Pageable paginacao) {
        LocalDate hoje = hoje();
        if (filtro.de() != null && filtro.ate() != null && filtro.de().isAfter(filtro.ate())) {
            throw new DadosInvalidosException("de", "A data inicial não pode ser posterior à data final.");
        }
        Sort ordenacao = ordenacao(paginacao.getSort());
        boolean fiscal = ator.perfil() == Perfil.FISCAL;
        List<Long> equipesDoFiscal = fiscal ? equipes.findIdsByMembro(ator.id()) : List.of();

        var especificacao = DemandaSpecification.listagem(filtro, hoje, equipesDoFiscal, fiscal ? ator.id() : null, ordenacao);
        Page<Demanda> pagina = demandas.findAll(especificacao, PageRequest.of(paginacao.getPageNumber(), paginacao.getPageSize()));
        return PageResponse.of(pagina, d -> resumo(d, hoje));
    }

    /** Demandas atribuídas a uma equipe (404 se a equipe não existe), opcionalmente filtradas por status. */
    @Transactional(readOnly = true)
    public PageResponse<DemandaResumo> listarPorEquipe(UsuarioAutenticado ator, Long equipeId, StatusDemanda status,
                                                       Pageable paginacao) {
        if (!equipes.existsById(equipeId)) {
            throw RecursoNaoEncontradoException.de("Equipe", equipeId);
        }
        DemandaFiltro filtro = new DemandaFiltro(null, status, null, null, null, equipeId, null, null, null, null);
        return listar(ator, filtro, paginacao);
    }

    @Transactional(readOnly = true)
    public DemandaDetalhe detalhar(UsuarioAutenticado ator, Long id) {
        Demanda demanda = obter(id);
        verificarEscopo(ator, demanda);
        return detalhe(demanda, hoje());
    }

    /** Linha do tempo da demanda, do evento mais recente ao mais antigo. */
    @Transactional(readOnly = true)
    public List<HistoricoItem> historico(UsuarioAutenticado ator, Long id) {
        Demanda demanda = obter(id);
        verificarEscopo(ator, demanda);
        return historicos.findLinhaDoTempo(id).stream()
                .map(h -> new HistoricoItem(h.getId(), h.getTipo(), h.getStatusAnterior(), h.getStatusNovo(),
                        h.getTexto(), UsuarioRef.de(h.getUsuario()), h.getData()))
                .toList();
    }

    // ------------------------------------------------------------------ regras

    private Demanda obter(Long id) {
        return demandas.buscarDetalhe(id).orElseThrow(() -> RecursoNaoEncontradoException.de("Demanda", id));
    }

    /** Lê a demanda com lock pessimista (SELECT ... FOR UPDATE): serializa edições/exclusões concorrentes. */
    private Demanda obterParaEscrita(Long id) {
        return demandas.buscarParaEscrita(id).orElseThrow(() -> RecursoNaoEncontradoException.de("Demanda", id));
    }

    private Usuario usuario(UsuarioAutenticado ator) {
        return usuarios.findById(ator.id()).orElseThrow(() -> RecursoNaoEncontradoException.de("Usuário", ator.id()));
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO));
    }

    /** FISCAL só acessa demandas atribuídas a ele ou da sua equipe; os demais perfis têm acesso total de leitura. */
    private void verificarEscopo(UsuarioAutenticado ator, Demanda demanda) {
        if (ator.perfil() != Perfil.FISCAL) {
            return;
        }
        boolean atribuida = demanda.getFiscal() != null && Objects.equals(demanda.getFiscal().getId(), ator.id());
        boolean daEquipe = demanda.getEquipe() != null
                && equipes.findIdsByMembro(ator.id()).contains(demanda.getEquipe().getId());
        if (!atribuida && !daEquipe) {
            throw new AccessDeniedException("Demanda fora do escopo do fiscal.");
        }
    }

    /** Validações de negócio por campo (além das anotações do DTO). Devolve o município de MT. */
    private Municipio validar(DemandaRequest pedido, LocalDate hoje) {
        List<ErroCampo> erros = new ArrayList<>();
        Municipio municipio = municipios.findById(pedido.municipioId()).orElse(null);
        if (municipio == null) {
            erros.add(new ErroCampo("municipioId",
                    "Município não encontrado entre os municípios de Mato Grosso (código IBGE)."));
        }
        if ((pedido.latitude() == null) != (pedido.longitude() == null)) {
            erros.add(new ErroCampo(pedido.latitude() == null ? "latitude" : "longitude",
                    "Informe latitude e longitude juntas."));
        }
        if (pedido.dataRecebimento().isAfter(hoje)) {
            erros.add(new ErroCampo("dataRecebimento", "A data de recebimento não pode ser futura."));
        }
        if (pedido.prazoSugerido() != null && pedido.prazoSugerido().isBefore(pedido.dataRecebimento())) {
            erros.add(new ErroCampo("prazoSugerido", "O prazo sugerido não pode ser anterior à data de recebimento."));
        }
        if (!erros.isEmpty()) {
            throw new DadosInvalidosException(erros);
        }
        return municipio;
    }

    private static void aplicar(Demanda demanda, DemandaRequest pedido, Municipio municipio) {
        demanda.setTitulo(pedido.titulo().trim());
        demanda.setDescricao(pedido.descricao().trim());
        demanda.setOrigem(pedido.origem());
        demanda.setCategoria(pedido.categoria());
        demanda.setMunicipio(municipio);
        demanda.setLocalidade(vazioParaNulo(pedido.localidade()));
        demanda.setLatitude(pedido.latitude());
        demanda.setLongitude(pedido.longitude());
        demanda.setDataRecebimento(pedido.dataRecebimento());
        demanda.setPrioridade(pedido.prioridade());
        demanda.setPrazoSugerido(pedido.prazoSugerido());
        demanda.setObservacoes(vazioParaNulo(pedido.observacoes()));
    }

    private static List<String> camposAlterados(Demanda d, DemandaRequest p) {
        List<String> campos = new ArrayList<>();
        if (!Objects.equals(d.getTitulo(), p.titulo().trim())) campos.add("título");
        if (!Objects.equals(d.getDescricao(), p.descricao().trim())) campos.add("descrição");
        if (d.getOrigem() != p.origem()) campos.add("origem");
        if (d.getCategoria() != p.categoria()) campos.add("categoria");
        if (!Objects.equals(d.getMunicipio().getId(), p.municipioId())) campos.add("município");
        if (!Objects.equals(d.getLocalidade(), vazioParaNulo(p.localidade()))) campos.add("localidade");
        if (!mesmoNumero(d.getLatitude(), p.latitude()) || !mesmoNumero(d.getLongitude(), p.longitude())) {
            campos.add("coordenadas");
        }
        if (!Objects.equals(d.getDataRecebimento(), p.dataRecebimento())) campos.add("data de recebimento");
        if (d.getPrioridade() != p.prioridade()) campos.add("prioridade");
        if (!Objects.equals(d.getPrazoSugerido(), p.prazoSugerido())) campos.add("prazo sugerido");
        if (!Objects.equals(d.getObservacoes(), vazioParaNulo(p.observacoes()))) campos.add("observações");
        return campos;
    }

    private static boolean mesmoNumero(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? a == b : a.compareTo(b) == 0;
    }

    private static String vazioParaNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private void registrarHistorico(Demanda demanda, TipoHistorico tipo, StatusDemanda anterior,
                                    StatusDemanda novo, String texto, Usuario autor) {
        historicos.save(new DemandaHistorico(demanda, tipo, anterior, novo, texto, autor));
    }

    /** Valida os campos de ordenação aceitos; sem ordenação, mais recentes primeiro. */
    private static Sort ordenacao(Sort pedida) {
        if (pedida.isUnsorted()) {
            return Sort.by(Sort.Order.desc("dataRecebimento"), Sort.Order.desc("id"));
        }
        List<String> invalidos = pedida.stream().map(Sort.Order::getProperty)
                .filter(p -> !DemandaSpecification.CAMPOS_ORDENAVEIS.contains(p)).toList();
        if (!invalidos.isEmpty()) {
            throw new DadosInvalidosException("sort", "Campo de ordenação inválido: " + invalidos.get(0)
                    + ". Use: " + DemandaSpecification.CAMPOS_ORDENAVEIS.stream().sorted()
                    .collect(Collectors.joining(", ")) + ".");
        }
        return pedida;
    }

    // ------------------------------------------------------------------ mapeamento

    private DemandaResumo resumo(Demanda d, LocalDate hoje) {
        return new DemandaResumo(d.getId(), d.getNumero(), d.getTitulo(), municipioRef(d.getMunicipio()), d.getOrigem(),
                d.getCategoria(), d.getPrioridade(), d.getStatus(), d.getDataRecebimento(), d.getPrazo(),
                d.atrasada(hoje), equipeRef(d.getEquipe()), UsuarioRef.de(d.getFiscal()));
    }

    private DemandaDetalhe detalhe(Demanda d, LocalDate hoje) {
        FiscalizacaoDetalhe fiscalizacao = d.getId() == null ? null
                : fiscalizacoes.buscarPorDemanda(d.getId()).map(DemandaService::fiscalizacao).orElse(null);
        return new DemandaDetalhe(d.getId(), d.getNumero(), d.getTitulo(), d.getDescricao(), d.getOrigem(),
                d.getCategoria(), municipioRef(d.getMunicipio()), d.getLocalidade(), d.getLatitude(), d.getLongitude(),
                d.getDataRecebimento(), d.getPrioridade(), d.getPrazoSugerido(), d.getPrazo(), d.getStatus(),
                d.atrasada(hoje), equipeRef(d.getEquipe()), UsuarioRef.de(d.getFiscal()), d.getObservacoes(),
                d.getResultadoFinal(), d.getJustificativaConclusao(), d.getConcluidaEm(), fiscalizacao,
                // Documentos (DocIA) chegam na Issue #15; até lá nenhuma demanda tem documento associado.
                0, UsuarioRef.de(d.getCriadoPor()), d.getCriadoEm(), d.getAtualizadoEm());
    }

    private static FiscalizacaoDetalhe fiscalizacao(Fiscalizacao f) {
        return new FiscalizacaoDetalhe(f.getId(), f.getData(), equipeRef(f.getEquipe()), f.getAtividades(),
                f.getResultado(), f.getEvidencias(), f.getRecomendacoes(), UsuarioRef.de(f.getRegistradoPor()),
                f.getRegistradoEm());
    }

    private static MunicipioRef municipioRef(Municipio m) {
        return new MunicipioRef(m.getId(), m.getNome(), m.getBioma());
    }

    private static EquipeRef equipeRef(Equipe e) {
        return e == null ? null : new EquipeRef(e.getId(), e.getNome());
    }
}
