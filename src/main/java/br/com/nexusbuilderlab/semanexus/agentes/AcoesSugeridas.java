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
        List<Documento> docs = new ArrayList<>();
        for (Long id : citados == null ? List.<Long>of() : citados) {
            if (id != null && docs.stream().noneMatch(d -> d.getId().equals(id))) {
                documentos.buscarAtivo(id).ifPresent(docs::add);
            }
        }
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
        // Documento citado que ainda não está na demanda do contexto: sugerir o vínculo (quem pode vincular).
        if (demandaId != null && ator != null && ator.perfil() != Perfil.CONSULTA) {
            DemandaDetalhe d = demandaAcessivel(ator, demandaId);
            if (d != null) {
                for (Documento doc : docs) {
                    if (r.size() < MAX_ACOES && !vinculado(doc.getId(), demandaId)) {
                        r.add(new AcaoSugerida(Tipo.SUGERIR_VINCULO_DEMANDA,
                                rotulo("Vincular \"" + doc.getTitulo() + "\" à demanda " + d.numero()),
                                parametros("documentoId", doc.getId(), "demandaId", demandaId)));
                    }
                }
            }
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

    private static boolean coordenadaValida(double lat, double lon) {
        return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    private boolean vinculado(Long documentoId, Long demandaId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM DOCUMENTO_DEMANDA WHERE DOCUMENTO_ID = ? AND DEMANDA_ID = ?", Integer.class,
                documentoId, demandaId);
        return n != null && n > 0;
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
