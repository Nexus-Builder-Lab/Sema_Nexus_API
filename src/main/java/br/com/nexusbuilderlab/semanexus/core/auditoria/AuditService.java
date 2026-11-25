package br.com.nexusbuilderlab.semanexus.core.auditoria;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.AuditoriaDto;
import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.AuditoriaFiltro;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Gravação centralizada de auditoria (versão mínima da Issue #4, ampliada na Issue #14).
 * O {@code detalhe} nunca deve conter senha, token ou outro dado sensível.
 */
@Service
public class AuditService {

    static final int LIMITE_DETALHE = 4000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> MAPA = new TypeReference<>() { };

    private static final Set<String> CAMPOS_ORDENAVEIS = Set.of("id", "data", "usuarioId", "acao", "entidade");
    private static final Sort ORDEM_PADRAO = Sort.by(Sort.Order.desc("data"), Sort.Order.desc("id"));
    /** Mascara pares chave=valor/chave: valor sensíveis, cabeçalhos Bearer e JWTs soltos. */
    private static final Pattern SENSIVEL = Pattern.compile(
            "(?i)((?:senha|password|token|secret|segredo|authorization)\\w*\"?\\s*[:=]\\s*\"?)(?:Bearer\\s+)?[^\\s\",;&}]+"
                    + "|(Bearer\\s+)[A-Za-z0-9._~+/=-]+"
                    + "|eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");

    private final AuditoriaRepository repositorio;
    private final UsuarioRepository usuarios;
    private final SeladorAuditoria selador;

    public AuditService(AuditoriaRepository repositorio, UsuarioRepository usuarios, SeladorAuditoria selador) {
        this.repositorio = repositorio;
        this.usuarios = usuarios;
        this.selador = selador;
    }

    /** Consulta paginada com filtros opcionais; sem {@code sort}, mais recentes primeiro. */
    @Transactional(readOnly = true)
    public PageResponse<AuditoriaDto> consultar(AuditoriaFiltro filtro, Pageable paginacao) {
        Sort ordem = paginacao.getSort().isSorted() && paginacao.getSort().stream()
                .allMatch(o -> CAMPOS_ORDENAVEIS.contains(o.getProperty()))
                ? paginacao.getSort() : ORDEM_PADRAO;
        Pageable pagina = PageRequest.of(paginacao.getPageNumber(), paginacao.getPageSize(), ordem);
        Page<Auditoria> resultado = repositorio.findAll(especificacao(filtro), pagina);
        Map<Long, String> nomes = nomes(resultado.getContent());
        return PageResponse.of(resultado.map(a -> paraDto(a, nomes)));
    }

    /** As {@code n} atividades mais recentes (usado pelo dashboard). */
    @Transactional(readOnly = true)
    public List<AuditoriaDto> recentes(int n) {
        int limite = Math.max(1, Math.min(n, 100));
        List<Auditoria> lista = repositorio.findAll(PageRequest.of(0, limite, ORDEM_PADRAO)).getContent();
        Map<Long, String> nomes = nomes(lista);
        return lista.stream().map(a -> paraDto(a, nomes)).toList();
    }

    /** Histórico (até 50 registros, do mais recente) de uma entidade específica. */
    @Transactional(readOnly = true)
    public List<AuditoriaDto> historicoDaEntidade(String entidade, String entidadeId) {
        List<Auditoria> lista = repositorio.findTop50ByEntidadeAndEntidadeIdOrderByDataDescIdDesc(entidade, entidadeId);
        Map<Long, String> nomes = nomes(lista);
        return lista.stream().map(a -> paraDto(a, nomes)).toList();
    }

    private static Specification<Auditoria> especificacao(AuditoriaFiltro f) {
        return (raiz, consulta, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f != null) {
                if (f.de() != null) {
                    ps.add(cb.greaterThanOrEqualTo(raiz.get("data"), f.de().atStartOfDay()));
                }
                if (f.ate() != null) {
                    ps.add(cb.lessThan(raiz.get("data"), f.ate().plusDays(1).atStartOfDay()));
                }
                if (f.usuarioId() != null) {
                    ps.add(cb.equal(raiz.get("usuarioId"), f.usuarioId()));
                }
                if (f.acao() != null && !f.acao().isBlank()) {
                    ps.add(cb.equal(cb.upper(raiz.get("acao")), f.acao().trim().toUpperCase(Locale.ROOT)));
                }
                if (f.entidade() != null && !f.entidade().isBlank()) {
                    ps.add(cb.equal(cb.upper(raiz.get("entidade")), f.entidade().trim().toUpperCase(Locale.ROOT)));
                }
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    private Map<Long, String> nomes(List<Auditoria> lista) {
        Set<Long> ids = lista.stream().map(Auditoria::getUsuarioId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> mapa = new HashMap<>();
        if (!ids.isEmpty()) {
            usuarios.findAllById(ids).forEach(u -> mapa.put(u.getId(), u.getNome()));
        }
        return mapa;
    }

    private static AuditoriaDto paraDto(Auditoria a, Map<Long, String> nomes) {
        return new AuditoriaDto(a.getId(), a.getData(), a.getUsuarioId(),
                a.getUsuarioId() == null ? null : nomes.get(a.getUsuarioId()),
                a.getAcao(), a.getEntidade(), a.getEntidadeId(), a.getDetalhe(),
                lerJson(a.getValorAnterior()), lerJson(a.getValorNovo()), a.getSeqCadeia(), a.getHashRegistro());
    }

    private static Map<String, String> lerJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, MAPA);
        } catch (JsonProcessingException e) {
            return Map.of("_bruto", json);
        }
    }

    static String sanear(String detalhe) {
        if (detalhe == null) {
            return null;
        }
        return SENSIVEL.matcher(detalhe).replaceAll(m ->
                m.group(1) != null ? Matcher.quoteReplacement(m.group(1) + "***")
                        : m.group(2) != null ? Matcher.quoteReplacement(m.group(2) + "***") : "***");
    }

    /** Registra a ação em nome do usuário autenticado no contexto de segurança (ou anônimo, se não houver). */
    @Transactional
    public void registrar(String acao, String entidade, String entidadeId, String detalhe) {
        registrarComo(usuarioAtualId(), acao, entidade, entidadeId, detalhe);
    }

    /** Registra a ação em nome do usuário informado (ex.: login, quando o contexto ainda está vazio). */
    @Transactional
    public void registrarComo(Long usuarioId, String acao, String entidade, String entidadeId, String detalhe) {
        gravar(new Auditoria(usuarioId, acao, entidade, entidadeId, limitar(sanear(detalhe))));
    }

    /**
     * Registra uma edição com os valores antes → depois dos campos alterados (Issue #148), em nome do usuário do
     * contexto de segurança. Sem campos alterados, registra só o detalhe.
     */
    @Transactional
    public void registrarAlteracao(String acao, String entidade, String entidadeId, String detalhe,
                                   Alteracoes alteracoes) {
        boolean vazia = alteracoes == null || alteracoes.vazia();
        gravar(new Auditoria(usuarioAtualId(), acao, entidade, entidadeId, limitar(sanear(detalhe)),
                vazia ? null : sanear(alteracoes.antesJson()), vazia ? null : sanear(alteracoes.depoisJson())));
    }

    private static String limitar(String texto) {
        return texto != null && texto.length() > LIMITE_DETALHE ? texto.substring(0, LIMITE_DETALHE) : texto;
    }

    /**
     * Insere na transação corrente e pede a selagem na cadeia SHA-256 depois do commit (num rollback o registro some
     * e nada é selado). Fora de transação, pede na hora.
     */
    private void gravar(Auditoria auditoria) {
        repositorio.save(auditoria);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Pedidos repetidos na mesma transação são agrupados pelo selador.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    selador.solicitarSelagem();
                }
            });
        } else {
            selador.solicitarSelagem();
        }
    }

    private static Long usuarioAtualId() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado u) {
            return u.id();
        }
        return null;
    }
}
