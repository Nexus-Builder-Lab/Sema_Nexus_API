package br.com.nexusbuilderlab.semanexus.agentes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import br.com.nexusbuilderlab.semanexus.agentes.AcaoSugerida.Tipo;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.docia.documento.Documento;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoSpecification;
import br.com.nexusbuilderlab.semanexus.flux.agente.SugestaoTriagem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteLoader;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Deriva as Action Cards (Issue #136) de forma determinística a partir do que o agente efetivamente citou ou
 * encontrou — os ids vêm das referências/duplicatas já validadas, nunca de texto do modelo — e revalida cada
 * parâmetro: o recurso existe e o usuário o acessa (escopo do FISCAL; CONSULTA não vincula documentos). Só lê.
 */
@Component
public class AcoesSugeridas {

    static final int MAX_ACOES = 5;
    static final int MAX_DEMANDAS = 3;
    static final int MAX_ROTULO = 90;
    static final int ZOOM_MAPA = 13;
    static final int MAX_NUMEROS_POR_DOCUMENTO = 5;
    private static final java.util.regex.Pattern NUMERO_DEMANDA =
            java.util.regex.Pattern.compile("\\bDEM-(\\d{4})/(\\d{4})\\b");

    private final DocumentoRepository documentos;
    private final JdbcTemplate jdbc;
    private final DemandaService demandas;

    public AcoesSugeridas(DocumentoRepository documentos, JdbcTemplate jdbc, DemandaService demandas) {
        this.documentos = documentos;
        this.jdbc = jdbc;
        this.demandas = demandas;
    }

    /**
     * DocIA: {@code citados} são os ids dos documentos citados na resposta; {@code demandaId} é a demanda do contexto
     * da pergunta (nula fora de uma demanda).
     */
    public List<AcaoSugerida> paraDocia(UsuarioAutenticado ator, Long demandaId, List<Long> citados) {
        List<AcaoSugerida> r = new ArrayList<>();
        List<Long> ids = (citados == null ? List.<Long>of() : citados).stream().filter(Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) {
            return r;
        }
        Map<Long, Documento> porId = new LinkedHashMap<>();
        documentos.findAllById(ids).forEach(d -> {
            if (d.getExcluidoEm() == null) {
                porId.put(d.getId(), d);
            }
        });
        List<Documento> docs = ids.stream().map(porId::get).filter(Objects::nonNull).toList();
        // Duas versões do mesmo documento citadas: comparação (aId = a mais antiga).
        for (int i = 0; i < docs.size() && r.isEmpty(); i++) {
            for (int j = i + 1; j < docs.size() && r.isEmpty(); j++) {
                Documento a = docs.get(i).getId() < docs.get(j).getId() ? docs.get(i) : docs.get(j);
                Documento b = a == docs.get(i) ? docs.get(j) : docs.get(i);
                if (mesmoDocumento(a, b)) {
                    r.add(new AcaoSugerida(Tipo.ABRIR_COMPARACAO,
                            rotulo("Comparar \"" + a.getTitulo() + "\" com \"" + b.getTitulo() + "\""),
                            parametros("aId", a.getId(), "bId", b.getId())));
                }
            }
        }
        // Documento cujo texto cita o número de uma demanda existente, acessível e à qual ainda não está vinculado.
        if (ator != null && ator.perfil() != Perfil.CONSULTA && !docs.isEmpty()) {
            Map<Long, List<String>> citacoes = new LinkedHashMap<>();
            java.util.Set<String> numeros = new java.util.LinkedHashSet<>();
            for (Documento d : docs) {
                List<String> n = numerosDeDemanda(d.getTextoExtraido());
                if (!n.isEmpty()) {
                    citacoes.put(d.getId(), n);
                    numeros.addAll(n);
                }
            }
            if (!numeros.isEmpty()) {
                Map<String, Long> demandaPorNumero = demandasPorNumero(numeros);
                java.util.Set<String> vinculos = vinculos(citacoes.keySet());
                for (Documento d : docs) {
                    for (String n : citacoes.getOrDefault(d.getId(), List.of())) {
                        Long dem = demandaPorNumero.get(n);
                        if (r.size() >= MAX_ACOES || dem == null || vinculos.contains(d.getId() + ":" + dem)) {
                            continue;
                        }
                        DemandaDetalhe det = demandaAcessivel(ator, dem);
                        if (det != null && r.stream().noneMatch(x -> x.tipo() == Tipo.SUGERIR_VINCULO_DEMANDA
                                && d.getId().equals(x.parametros().get("documentoId"))
                                && dem.equals(x.parametros().get("demandaId")))) {
                            r.add(new AcaoSugerida(Tipo.SUGERIR_VINCULO_DEMANDA,
                                    rotulo("Vincular \"" + d.getTitulo() + "\" à demanda " + det.numero()),
                                    parametros("documentoId", d.getId(), "demandaId", dem)));
                        }
                    }
                }
            }
        }
        return r.size() > MAX_ACOES ? new ArrayList<>(r.subList(0, MAX_ACOES)) : r;
    }

    static List<String> numerosDeDemanda(String texto) {
        List<String> r = new ArrayList<>();
        java.util.regex.Matcher m = NUMERO_DEMANDA.matcher(texto == null ? "" : texto);
        while (m.find() && r.size() < MAX_NUMEROS_POR_DOCUMENTO) {
            String n = "DEM-" + m.group(1) + "/" + m.group(2);
            if (!r.contains(n)) {
                r.add(n);
            }
        }
        return r;
    }

    private Map<String, Long> demandasPorNumero(java.util.Collection<String> numeros) {
        Map<String, Long> r = new LinkedHashMap<>();
        String in = String.join(",", java.util.Collections.nCopies(numeros.size(), "?"));
        for (Map<String, Object> l : jdbc.queryForList("SELECT ID, NUMERO FROM DEMANDA WHERE NUMERO IN (" + in + ")",
                numeros.toArray())) {
            r.put(String.valueOf(l.get("NUMERO")), ((Number) l.get("ID")).longValue());
        }
        return r;
    }

    /** Pares "documentoId:demandaId" já vinculados, numa só consulta. */
    private java.util.Set<String> vinculos(java.util.Collection<Long> documentoIds) {
        java.util.Set<String> r = new java.util.HashSet<>();
        String in = String.join(",", java.util.Collections.nCopies(documentoIds.size(), "?"));
        for (Map<String, Object> l : jdbc.queryForList(
                "SELECT DOCUMENTO_ID, DEMANDA_ID FROM DOCUMENTO_DEMANDA WHERE DOCUMENTO_ID IN (" + in + ")",
                documentoIds.toArray())) {
            r.add(((Number) l.get("DOCUMENTO_ID")).longValue() + ":" + ((Number) l.get("DEMANDA_ID")).longValue());
        }
        return r;
    }

    /** Triagem: duplicatas (demandas abertas próximas/semelhantes) e a localização da própria demanda. */
    public List<AcaoSugerida> paraTriagem(UsuarioAutenticado ator, DemandaDetalhe demanda,
                                          List<SugestaoTriagem.Duplicata> duplicatas,
                                          SugestaoTriagem.EvidenciaEspacial espacial) {
        List<AcaoSugerida> r = new ArrayList<>();
        if (ator == null || demanda == null) {
            return r;
        }
        for (SugestaoTriagem.Duplicata dup : duplicatas == null ? List.<SugestaoTriagem.Duplicata>of() : duplicatas) {
            if (r.size() >= MAX_DEMANDAS) {
                break;
            }
            if (dup == null || dup.id() == null || Objects.equals(dup.id(), demanda.id())
                    || r.stream().anyMatch(a -> Objects.equals(a.parametros().get("demandaId"), dup.id()))) {
                continue;
            }
            DemandaDetalhe d = demandaAcessivel(ator, dup.id());
            if (d != null) {
                r.add(new AcaoSugerida(Tipo.ABRIR_DEMANDA, rotulo("Abrir demanda " + d.numero()),
                        parametros("demandaId", d.id())));
            }
        }
        if (espacial != null && espacial.latitude() != null && espacial.longitude() != null
                && !"MUNICIPIO".equals(espacial.referencia())
                && coordenadaValida(espacial.latitude(), espacial.longitude())) {
            r.add(new AcaoSugerida(Tipo.VER_NO_MAPA, rotulo("Ver demanda " + demanda.numero() + " no mapa"),
                    parametros("lat", espacial.latitude(), "lon", espacial.longitude(), "zoom", ZOOM_MAPA,
                            "demandaId", demanda.id())));
        }
        return r;
    }

    // ------------------------------------------------------------------------------------------------- regras

    /** Mesmo tipo, categoria e município e título igual sem marcas de versão: versões do mesmo documento. */
    static boolean mesmoDocumento(Documento a, Documento b) {
        if (a.getTipo() == null || !a.getTipo().equals(b.getTipo())
                || !Objects.equals(a.getCategoria(), b.getCategoria())) {
            return false;
        }
        Integer ma = a.getMunicipio() == null ? null : a.getMunicipio().getId();
        Integer mb = b.getMunicipio() == null ? null : b.getMunicipio().getId();
        if (!Objects.equals(ma, mb) || vazio(a.getTextoExtraido()) || vazio(b.getTextoExtraido())) {
            return false; // sem texto extraído a comparação do servidor responderia 400
        }
        String ta = tituloBase(a.getTitulo());
        return !ta.isEmpty() && ta.equals(tituloBase(b.getTitulo()));
    }

    static String tituloBase(String titulo) {
        String t = DocumentoSpecification.normalizar(titulo == null ? "" : titulo)
                .replaceAll("\\(.*?\\)", " ")
                .replaceAll("\\b(versao|ver|v|rev|revisao)\\.?\\s*\\d+([.,]\\d+)*\\b", " ")
                .replaceAll("[^a-z0-9]+", " ").strip();
        return t.replaceAll("\\s+", " ");
    }

    private static final double LAT_MIN = ReferenciaSateliteLoader.LAT_MIN.doubleValue();
    private static final double LAT_MAX = ReferenciaSateliteLoader.LAT_MAX.doubleValue();
    private static final double LON_MIN = ReferenciaSateliteLoader.LON_MIN.doubleValue();
    private static final double LON_MAX = ReferenciaSateliteLoader.LON_MAX.doubleValue();

    private static boolean coordenadaValida(double lat, double lon) {
        return lat >= LAT_MIN && lat <= LAT_MAX && lon >= LON_MIN && lon <= LON_MAX; // 0,0 fica fora
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    /** Detalhe da demanda se ela existe e o usuário a acessa (escopo do FISCAL); {@code null} caso contrário. */
    private DemandaDetalhe demandaAcessivel(UsuarioAutenticado ator, Long id) {
        try {
            return demandas.detalhar(ator, id);
        } catch (AccessDeniedException | RecursoNaoEncontradoException e) {
            return null;
        }
    }

    private static String rotulo(String s) {
        return s.length() > MAX_ROTULO ? s.substring(0, MAX_ROTULO - 1).strip() + "…" : s;
    }

    private static Map<String, Object> parametros(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
