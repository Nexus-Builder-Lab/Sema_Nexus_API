package br.com.nexusbuilderlab.semanexus.agentes.global;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import br.com.nexusbuilderlab.semanexus.agentes.global.RespostaAssistenteGlobal.LinkAssistente;
import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.docia.assistente.BuscaDocumentos;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.PrazoService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaFiltro;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaResumo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.PrazoDtos.EstadoPrazo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.PrazoDtos.PrazoItem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.PrazoDtos.PrazoResumo;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.IndicadorService;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorFiltro;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.ResumoIndicadores;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.ContagemMunicipio;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.PorMunicipioResponse;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaFiltro;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteService;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.TipoReferencia;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Ferramentas do Assistente Global (Issue #139). Cada uma <b>só consulta</b> e delega a um Service existente com o
 * usuário logado ({@code ator}), que aplica o mesmo escopo dos endpoints: o FISCAL só vê demandas da sua equipe ou
 * atribuídas a ele (listagem e prazos); indicadores são os agregados do {@code GET /api/intelligence/resumo} (aberto a
 * todos os perfis, sem listar demandas); documentos são a biblioteca (aberta a todos os perfis) e o INPE é dado
 * público. Os links são montados aqui, a partir dos registros devolvidos pelos Services — nunca pelo modelo.
 */
@Component
public class FerramentasAssistenteGlobal {

    static final int MAX_ITENS = 5;
    static final int MAX_TEXTO_BUSCA = 100;
    static final int MAX_TRECHO = 300;

    /** Resultado de uma ferramenta: dados (para o modelo), resumo determinístico e links. */
    public record Consulta(String ferramenta, Map<String, Object> dados, String resumo, List<LinkAssistente> links) {
    }

    private final IndicadorService indicadores;
    private final PrazoService prazos;
    private final DemandaService demandas;
    private final BuscaDocumentos busca;
    private final ReferenciaSateliteService referencia;
    private final MunicipioRepository municipios;

    public FerramentasAssistenteGlobal(IndicadorService indicadores, PrazoService prazos, DemandaService demandas,
                                       BuscaDocumentos busca, ReferenciaSateliteService referencia,
                                       MunicipioRepository municipios) {
        this.indicadores = indicadores;
        this.prazos = prazos;
        this.demandas = demandas;
        this.busca = busca;
        this.referencia = referencia;
        this.municipios = municipios;
    }

    // ------------------------------------------------------------------ ferramentas

    public Consulta indicadores(String municipio, LocalDate de, LocalDate ate) {
        validarPeriodo(de, ate);
        Municipio m = resolverMunicipio(municipio);
        Integer municipioId = m == null ? null : m.getId();
        ResumoIndicadores r = indicadores.resumo(new IndicadorFiltro(de, ate, municipioId, null, null, null, null));
        String onde = m == null ? "Mato Grosso" : m.getNome();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("abrangencia", onde);
        d.put("de", texto(de));
        d.put("ate", texto(ate));
        d.put("totalDemandas", r.totalDemandas());
        d.put("pendentes", r.pendentes());
        d.put("emAndamento", r.emAndamento());
        d.put("finalizadas", r.finalizadas());
        d.put("atrasadas", r.atrasadas());
        d.put("fiscalizacoes", r.fiscalizacoes());
        d.put("totalDocumentos", r.totalDocumentos());
        d.put("percentualConclusao", r.percentualConclusao());
        d.put("tempoMedioAtendimentoDias", r.tempoMedioAtendimentoDias());
        String resumo = "Indicadores (" + onde + periodo(de, ate) + "): " + r.totalDemandas() + " demanda(s), "
                + r.pendentes() + " pendente(s), " + r.emAndamento() + " em andamento, " + r.finalizadas()
                + " finalizada(s) e " + r.atrasadas() + " atrasada(s).";
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("municipioId", municipioId);
        q.put("de", de);
        q.put("ate", ate);
        return new Consulta("resumo_indicadores_ambientais", d, resumo,
                List.of(new LinkAssistente("TELA", "Painel de indicadores — " + onde, url("/intelligence", q))));
    }

    /** {@code estado} nulo = vencidas e próximas do vencimento. */
    public Consulta prazosCriticos(UsuarioAutenticado ator, EstadoPrazo estado, Integer diasAlerta) {
        if (estado == EstadoPrazo.NO_PRAZO || estado == EstadoPrazo.CONCLUIDA) {
            throw new IllegalArgumentException("estado deve ser VENCIDA ou PROXIMA");
        }
        PrazoResumo r = prazos.resumo(ator, diasAlerta);
        List<PrazoItem> itens = new ArrayList<>();
        if (estado == null || estado == EstadoPrazo.VENCIDA) {
            itens.addAll(prazos.listar(ator, EstadoPrazo.VENCIDA, diasAlerta, PageRequest.of(0, MAX_ITENS)).content());
        }
        if (estado == null || estado == EstadoPrazo.PROXIMA) {
            itens.addAll(prazos.listar(ator, EstadoPrazo.PROXIMA, diasAlerta, PageRequest.of(0, MAX_ITENS)).content());
        }
        List<PrazoItem> primeiros = itens.stream().limit(MAX_ITENS).toList();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("escopo", escopo(ator));
        d.put("vencidas", r.vencidas());
        d.put("proximasDoVencimento", r.proximas());
        d.put("maisAntigaVencida", r.maisAntigaVencida() == null ? null : Map.of(
                "numero", r.maisAntigaVencida().numero(), "diasAtraso", r.maisAntigaVencida().diasAtraso()));
        List<Map<String, Object>> lista = new ArrayList<>();
        List<LinkAssistente> links = new ArrayList<>();
        for (PrazoItem i : primeiros) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numero", i.numero());
            m.put("titulo", i.titulo());
            m.put("municipio", i.municipio());
            m.put("prazo", texto(i.prazo()));
            m.put("estado", i.estado() == null ? null : i.estado().name());
            m.put("diasAtraso", i.diasAtraso());
            m.put("diasRestantes", i.diasRestantes());
            lista.add(m);
            links.add(new LinkAssistente("DEMANDA", i.numero() + " — " + i.titulo(), "/flux/demandas/" + i.id()));
        }
        d.put("itens", lista);
        links.add(new LinkAssistente("TELA", "Controle de prazos", "/flux/prazos"));
        StringBuilder resumo = new StringBuilder("Prazos (" + escopo(ator) + "): " + r.vencidas()
                + " demanda(s) vencida(s) e " + r.proximas() + " próxima(s) do vencimento.");
        if (r.maisAntigaVencida() != null) {
            resumo.append(" Vencida há mais tempo: ").append(r.maisAntigaVencida().numero()).append(" (")
                    .append(r.maisAntigaVencida().diasAtraso()).append(" dia(s) de atraso).");
        }
        return new Consulta("prazos_criticos", d, resumo.toString(), links);
    }

    public Consulta buscarDemandas(UsuarioAutenticado ator, String texto, StatusDemanda status, String municipio) {
        String q = texto == null || texto.isBlank() ? null : cortar(texto.strip(), MAX_TEXTO_BUSCA);
        Municipio m = resolverMunicipio(municipio);
        Integer municipioId = m == null ? null : m.getId();
        // Só texto, status e município: equipe/fiscal não são parâmetros da ferramenta, e o DemandaService aplica o
        // escopo do FISCAL (equipe ou atribuídas a ele) sobre qualquer filtro.
        PageResponse<DemandaResumo> pagina = demandas.listar(ator,
                new DemandaFiltro(q, status, municipioId, null, null, null, null, null, null, null),
                PageRequest.of(0, MAX_ITENS));
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("escopo", escopo(ator));
        d.put("filtros", filtros(q, status, m));
        d.put("total", pagina.totalElements());
        List<Map<String, Object>> lista = new ArrayList<>();
        List<LinkAssistente> links = new ArrayList<>();
        for (DemandaResumo x : pagina.content()) {
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("numero", x.numero());
            i.put("titulo", x.titulo());
            i.put("status", x.status() == null ? null : x.status().rotulo());
            i.put("municipio", x.municipio() == null ? null : x.municipio().nome());
            i.put("prioridade", x.prioridade() == null ? null : x.prioridade().name());
            i.put("prazo", texto(x.prazo()));
            i.put("atrasada", x.atrasada());
            lista.add(i);
            links.add(new LinkAssistente("DEMANDA", x.numero() + " — " + x.titulo(), "/flux/demandas/" + x.id()));
        }
        d.put("itens", lista);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", q);
        params.put("status", status == null ? null : status.name());
        params.put("municipioId", municipioId);
        links.add(new LinkAssistente("TELA", "Lista de demandas filtrada", url("/flux/demandas", params)));
        String resumo = "Demandas (" + escopo(ator) + filtrosTexto(q, status, m) + "): " + pagina.totalElements()
                + " encontrada(s)" + (pagina.content().isEmpty() ? "." : "; as mais recentes: "
                + String.join(", ", pagina.content().stream().map(DemandaResumo::numero).toList()) + ".");
        return new Consulta("buscar_demandas", d, resumo, links);
    }

    public Consulta buscarDocumentos(String consulta) {
        if (consulta == null || consulta.isBlank()) {
            throw new IllegalArgumentException("consulta obrigatória");
        }
        String c = cortar(consulta.strip(), MAX_TEXTO_BUSCA);
        BuscaDocumentos.Resultado r = busca.pesquisar(c, MAX_ITENS, null);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("consulta", c);
        d.put("recuperacao", r.recuperacao() == null ? null : r.recuperacao().name());
        List<Map<String, Object>> lista = new ArrayList<>();
        List<LinkAssistente> links = new ArrayList<>();
        for (BuscaDocumentos.Achado a : r.achados()) {
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("titulo", a.documento().getTitulo());
            i.put("aderenciaTextual", a.aderencia());
            i.put("trecho", a.trecho() == null ? null : cortar(a.trecho(), MAX_TRECHO));
            lista.add(i);
            links.add(new LinkAssistente("DOCUMENTO", a.documento().getTitulo(),
                    "/docia/documentos/" + a.documento().getId()));
        }
        d.put("itens", lista);
        links.add(new LinkAssistente("TELA", "Biblioteca de documentos filtrada",
                url("/docia/documentos", Map.of("q", c))));
        String resumo = r.achados().isEmpty()
                ? "Documentos (\"" + c + "\"): nenhum documento encontrado."
                : "Documentos (\"" + c + "\"): " + r.achados().size() + " encontrado(s); o mais aderente: "
                        + r.achados().get(0).documento().getTitulo() + ".";
        return new Consulta("buscar_documentos", d, resumo, links);
    }

    public Consulta alertasInpe(String municipio, LocalDate de, LocalDate ate) {
        validarPeriodo(de, ate);
        Municipio m = resolverMunicipio(municipio);
        Integer municipioId = m == null ? null : m.getId();
        PorMunicipioResponse focos = referencia.porMunicipio(
                new ReferenciaFiltro(TipoReferencia.FOCO_CALOR, de, ate, municipioId, null));
        PorMunicipioResponse deter = referencia.porMunicipio(
                new ReferenciaFiltro(TipoReferencia.DESMATAMENTO, de, ate, municipioId, null));
        String onde = m == null ? "Mato Grosso" : m.getNome();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("fonte", "INPE — dados públicos");
        d.put("abrangencia", onde);
        d.put("de", texto(de));
        d.put("ate", texto(ate));
        d.put("focosDeCalor", focos.total());
        d.put("alertasDesmatamentoDeter", deter.total());
        if (m == null) {
            d.put("municipiosComMaisFocos", focos.itens().stream()
                    .sorted(Comparator.comparingLong(ContagemMunicipio::total).reversed())
                    .limit(MAX_ITENS)
                    .map(x -> {
                        Map<String, Object> i = new LinkedHashMap<>();
                        i.put("municipio", x.municipio());
                        i.put("focos", x.total());
                        return i;
                    })
                    .toList());
        }
        String resumo = "INPE — dados públicos (" + onde + periodo(de, ate) + "): " + focos.total()
                + " foco(s) de calor e " + deter.total() + " alerta(s) de desmatamento DETER.";
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("municipioId", municipioId);
        q.put("de", de);
        q.put("ate", ate);
        return new Consulta("resumo_alertas_inpe", d, resumo,
                List.of(new LinkAssistente("TELA", "Mapa e indicadores — " + onde, url("/intelligence", q))));
    }

    // ------------------------------------------------------------------ apoio

    /** Município pelo código IBGE ou pelo nome (sem acento/caixa); nulo se vazio; inválido se não existir. */
    Municipio resolverMunicipio(String municipio) {
        if (municipio == null || municipio.isBlank()) {
            return null;
        }
        String m = municipio.strip();
        List<Municipio> todos = municipios.findAllByOrderByNomeAsc();
        if (m.matches("\\d{7}")) {
            int id = Integer.parseInt(m);
            return todos.stream().filter(x -> x.getId() == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("município inexistente"));
        }
        String n = normalizar(m);
        return todos.stream().filter(x -> normalizar(x.getNome()).equals(n)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("município inexistente"));
    }

    /** Município citado na pergunta (o nome mais longo encontrado, por palavra inteira). Modo demonstrativo. */
    Optional<Municipio> municipioNoTexto(String texto) {
        String t = " " + normalizar(texto).replaceAll("[^a-z0-9]+", " ") + " ";
        return municipios.findAllByOrderByNomeAsc().stream()
                .filter(x -> t.contains(" " + normalizar(x.getNome()).replaceAll("[^a-z0-9]+", " ").strip() + " "))
                .max(Comparator.comparingInt(x -> x.getNome().length()));
    }

    static String normalizar(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).strip();
    }

    static LocalDate data(String s, String campo) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(campo + " deve estar no formato AAAA-MM-DD", e);
        }
    }

    private static void validarPeriodo(LocalDate de, LocalDate ate) {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new IllegalArgumentException("de posterior a ate");
        }
    }

    private static String escopo(UsuarioAutenticado ator) {
        return switch (ator.perfil()) {
            case FISCAL -> "suas equipes e demandas atribuídas a você";
            default -> "todas as demandas visíveis ao perfil " + ator.perfil().name();
        };
    }

    private static Map<String, Object> filtros(String q, StatusDemanda status, Municipio m) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("texto", q);
        f.put("status", status == null ? null : status.rotulo());
        f.put("municipio", m == null ? null : m.getNome());
        return f;
    }

    private static String filtrosTexto(String q, StatusDemanda status, Municipio m) {
        StringBuilder s = new StringBuilder();
        if (q != null) {
            s.append("; texto \"").append(q).append('"');
        }
        if (status != null) {
            s.append("; status ").append(status.rotulo());
        }
        if (m != null) {
            s.append("; ").append(m.getNome());
        }
        return s.toString();
    }

    private static String periodo(LocalDate de, LocalDate ate) {
        if (de == null && ate == null) {
            return "";
        }
        return "; " + (de == null ? "até " + ate : ate == null ? "desde " + de : de + " a " + ate);
    }

    private static String texto(Object o) {
        return o == null ? null : o.toString();
    }

    private static String cortar(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Caminho do Web com os parâmetros não nulos, codificados. */
    static String url(String caminho, Map<String, Object> params) {
        StringJoiner q = new StringJoiner("&");
        params.forEach((k, v) -> {
            if (v != null) {
                q.add(k + "=" + URLEncoder.encode(v.toString(), StandardCharsets.UTF_8));
            }
        });
        return q.length() == 0 ? caminho : caminho + "?" + q;
    }
}
