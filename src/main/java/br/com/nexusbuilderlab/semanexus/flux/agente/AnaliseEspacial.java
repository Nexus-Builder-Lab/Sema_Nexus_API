package br.com.nexusbuilderlab.semanexus.flux.agente;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.nexusbuilderlab.semanexus.common.geo.GeoJson;
import br.com.nexusbuilderlab.semanexus.common.geo.Geodesia;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoSpecification;
import br.com.nexusbuilderlab.semanexus.flux.agente.SugestaoTriagem.DocumentoRelacionado;
import br.com.nexusbuilderlab.semanexus.flux.agente.SugestaoTriagem.EvidenciaEspacial;
import br.com.nexusbuilderlab.semanexus.flux.agente.SugestaoTriagem.Inpe;
import br.com.nexusbuilderlab.semanexus.flux.agente.SugestaoTriagem.Proxima;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaFiltro;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaProxima;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaResumo;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaFiltro;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteRepository.PontoEspacial;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteService;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.TipoReferencia;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Consultas geoespaciais somente-leitura do Agente de Triagem (Issue #132): focos de calor e poligonos DETER num raio,
 * demandas proximas (Haversine) e documentos da mesma localidade. Pre-filtro por caixa envolvente em SQL e
 * refinamento em Java. Sem coordenada nem area, cai no municipio (referencia {@code MUNICIPIO}). Nada e gravado.
 */
@Service
public class AnaliseEspacial {

    public static final double RAIO_PADRAO_KM = 10;
    static final double RAIO_MAXIMO_KM = 50;
    static final int MAX_PROXIMAS = 8;
    static final int MAX_DOCUMENTOS = 8;
    static final int LIMITE_PONTOS = 5000;
    /** Margem da caixa para poligonos DETER, cujo centroide pode estar longe de uma borda que toca o raio. */
    static final double MARGEM_DETER_KM = 30;

    /** Ponto de referencia da analise. */
    record Ponto(String origem, Double lat, Double lon) {
        boolean temCoordenada() {
            return lat != null;
        }
    }

    private final DemandaService demandas;
    private final ReferenciaSateliteService referencia;
    private final JdbcTemplate jdbc;

    public AnaliseEspacial(DemandaService demandas, ReferenciaSateliteService referencia, JdbcTemplate jdbc) {
        this.demandas = demandas;
        this.referencia = referencia;
        this.jdbc = jdbc;
    }

    static double raio(Double pedido) {
        if (pedido == null || pedido.isNaN() || pedido <= 0) {
            return RAIO_PADRAO_KM;
        }
        return Math.min(pedido, RAIO_MAXIMO_KM);
    }

    /** Coordenada da demanda; senao centroide da area; senao sem ponto (fallback ao municipio). */
    static Ponto ponto(DemandaDetalhe d) {
        if (d.latitude() != null && d.longitude() != null) {
            return new Ponto("COORDENADA", d.latitude().doubleValue(), d.longitude().doubleValue());
        }
        double[] c = Geodesia.centroide(d.areaGeometria());
        return c == null ? new Ponto("MUNICIPIO", null, null) : new Ponto("AREA", c[0], c[1]);
    }

    public EvidenciaEspacial analisar(UsuarioAutenticado ator, DemandaDetalhe d, Double raioKm) {
        double raio = raio(raioKm);
        Ponto p = ponto(d);
        List<Proxima> proximas = proximas(ator, d, raio);
        return new EvidenciaEspacial(p.origem(), p.lat(), p.lon(), raio, inpe(d, raio), proximas,
                documentos(d, proximas));
    }

    // -------------------------------------------------------------------------------------------- INPE

    public Inpe inpe(DemandaDetalhe d, double raio) {
        long focosMun = contarMunicipio(TipoReferencia.FOCO_CALOR, d.municipio().id());
        long deterMun = contarMunicipio(TipoReferencia.DESMATAMENTO, d.municipio().id());
        Ponto p = ponto(d);
        if (!p.temCoordenada()) {
            return new Inpe(0, null, 0, false, null, focosMun, deterMun);
        }
        try {
            var c = Geodesia.caixa(p.lat(), p.lon(), raio, 0);
            long focos = 0;
            Double maisProximo = null;
            for (PontoEspacial f : referencia.naCaixa(TipoReferencia.FOCO_CALOR, c.latMin(), c.latMax(), c.lonMin(),
                    c.lonMax(), false, LIMITE_PONTOS)) {
                double km = Geodesia.haversineKm(p.lat(), p.lon(), f.latitude(), f.longitude());
                if (km <= raio) {
                    focos++;
                    maisProximo = maisProximo == null ? km : Math.min(maisProximo, km);
                }
            }
            var cd = Geodesia.caixa(p.lat(), p.lon(), raio, MARGEM_DETER_KM);
            long alertas = 0;
            boolean dentro = false;
            Double menor = null;
            for (PontoEspacial a : referencia.naCaixa(TipoReferencia.DESMATAMENTO, cd.latMin(), cd.latMax(),
                    cd.lonMin(), cd.lonMax(), true, LIMITE_PONTOS)) {
                double km = distanciaDeter(p, a);
                if (km <= raio) {
                    alertas++;
                    dentro |= km == 0;
                    menor = menor == null ? km : Math.min(menor, km);
                }
            }
            return new Inpe(focos, arredondar(maisProximo), alertas, dentro, arredondar(menor), focosMun, deterMun);
        } catch (RuntimeException e) {
            return new Inpe(0, null, 0, false, null, focosMun, deterMun);
        }
    }

    /** Distancia do ponto ao poligono DETER; sem geometria valida usa a distancia ao centroide do alerta. */
    private static double distanciaDeter(Ponto p, PontoEspacial a) {
        double km = Geodesia.distanciaAoPoligonoKm(p.lat(), p.lon(), GeoJson.geometria(a.geometria()));
        return Double.isNaN(km) ? Geodesia.haversineKm(p.lat(), p.lon(), a.latitude(), a.longitude()) : km;
    }

    private long contarMunicipio(TipoReferencia tipo, Integer municipioId) {
        try {
            return referencia.porMunicipio(new ReferenciaFiltro(tipo, null, null, municipioId, null)).total();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------------------------ demandas

    /** Demandas nao finalizadas no raio (escopo do usuario, mesmo alem da divisa); sem coordenada, as do municipio. */
    public List<Proxima> proximas(UsuarioAutenticado ator, DemandaDetalhe d, double raio) {
        Ponto p = ponto(d);
        List<Proxima> r = new ArrayList<>();
        if (p.temCoordenada()) {
            for (DemandaProxima x : demandas.proximas(ator, p.lat(), p.lon(), raio, d.id(), MAX_PROXIMAS)) {
                r.add(proxima(x.demanda(), arredondar(x.distanciaKm())));
            }
            return r;
        }
        DemandaFiltro f = new DemandaFiltro(null, null, d.municipio().id(), null, null, null, null, null, null, null);
        for (DemandaResumo x : demandas.listar(ator, f, PageRequest.of(0, MAX_PROXIMAS * 3)).content()) {
            if (!x.id().equals(d.id()) && x.status() != StatusDemanda.FINALIZADA && r.size() < MAX_PROXIMAS) {
                r.add(proxima(x, null));
            }
        }
        return r;
    }

    private static Proxima proxima(DemandaResumo x, Double km) {
        return new Proxima(x.id(), x.numero(), x.titulo(), x.status(), x.categoria(), x.municipio().nome(), km);
    }

    // ---------------------------------------------------------------------------------- documentos

    /**
     * Documentos ativos: que citam a localidade (no municipio da demanda), vinculados a demandas proximas e, por
     * fim, do mesmo municipio. Documentos nao tem coordenada propria: a proximidade vem das demandas vinculadas.
     */
    public List<DocumentoRelacionado> documentos(DemandaDetalhe d, List<Proxima> proximas) {
        Map<Long, DocumentoRelacionado> r = new LinkedHashMap<>();
        try {
            String loc = d.localidade();
            if (loc != null && loc.strip().length() >= 3) {
                String padrao = DocumentoSpecification.padraoBusca(loc);
                List<Long> citam = jdbc.queryForList(DocumentoSpecification.SQL_BUSCA, Long.class,
                        padrao, padrao, padrao, padrao);
                if (!citam.isEmpty()) {
                    jdbc.query("SELECT ID, TITULO FROM DOCUMENTO WHERE EXCLUIDO_EM IS NULL AND MUNICIPIO_ID = ? "
                                    + "AND ID IN (" + marcas(citam.size()) + ") FETCH FIRST ? ROWS ONLY",
                            (rs, i) -> r.putIfAbsent(rs.getLong("ID"), new DocumentoRelacionado(rs.getLong("ID"),
                                    rs.getString("TITULO"), "Cita a localidade \"" + loc.strip() + "\"")),
                            argumentos(d.municipio().id(), citam, MAX_DOCUMENTOS));
                }
            }
            List<Long> ids = proximas.stream().map(Proxima::id).toList();
            if (!ids.isEmpty()) {
                jdbc.query("SELECT DISTINCT doc.ID, doc.TITULO, dd.DEMANDA_ID FROM DOCUMENTO_DEMANDA dd "
                                + "JOIN DOCUMENTO doc ON doc.ID = dd.DOCUMENTO_ID WHERE doc.EXCLUIDO_EM IS NULL "
                                + "AND dd.DEMANDA_ID IN (" + marcas(ids.size()) + ") FETCH FIRST ? ROWS ONLY",
                        (rs, i) -> {
                            long demandaId = rs.getLong("DEMANDA_ID");
                            Proxima x = proximas.stream().filter(y -> y.id() == demandaId).findFirst().orElseThrow();
                            return r.putIfAbsent(rs.getLong("ID"), new DocumentoRelacionado(rs.getLong("ID"),
                                    rs.getString("TITULO"), "Vinculado a demanda " + x.numero()
                                    + (x.distanciaKm() == null ? "" : " (" + x.distanciaKm() + " km)")));
                        }, argumentos(null, ids, MAX_DOCUMENTOS));
            }
            jdbc.query("SELECT ID, TITULO FROM DOCUMENTO WHERE EXCLUIDO_EM IS NULL AND MUNICIPIO_ID = ? "
                            + "ORDER BY DATA_DOCUMENTO DESC FETCH FIRST ? ROWS ONLY",
                    (rs, i) -> r.putIfAbsent(rs.getLong("ID"), new DocumentoRelacionado(rs.getLong("ID"),
                            rs.getString("TITULO"), "Mesmo municipio (" + d.municipio().nome() + ")")),
                    d.municipio().id(), MAX_DOCUMENTOS);
        } catch (RuntimeException e) {
            // consulta auxiliar: devolve o que ja foi reunido
        }
        return r.values().stream().limit(MAX_DOCUMENTOS).toList();
    }

    private static String marcas(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    private static Object[] argumentos(Integer municipio, List<Long> ids, int limite) {
        List<Object> a = new ArrayList<>();
        if (municipio != null) {
            a.add(municipio);
        }
        a.addAll(ids);
        a.add(limite);
        return a.toArray();
    }

    static Double arredondar(Double km) {
        return km == null ? null : BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
