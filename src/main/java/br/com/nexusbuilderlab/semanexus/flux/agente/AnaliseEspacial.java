package br.com.nexusbuilderlab.semanexus.flux.agente;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.SobreposicaoCamadasPublicas;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaService;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaFiltro;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteRepository.PontoEspacial;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteService;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.TipoReferencia;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
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
    /** Máximo de focos lidos (os mais próximos do centro); acima disso a contagem é marcada como truncada. */
    static final int LIMITE_FOCOS = 2000;
    /** Máximo de candidatos DETER cuja geometria (CLOB) é lida. */
    static final int LIMITE_DETER = 300;
    /** Máximo de documentos citando a localidade usados no IN (limite de 1000 do Oracle). */
    static final int LIMITE_CITAM = 900;
    /** Margem da caixa para poligonos DETER, cujo centroide pode estar longe de uma borda que toca o raio. */
    static final double MARGEM_DETER_KM = 30;

    /** Ponto de referencia da analise. */
    record Ponto(String origem, Double lat, Double lon, Map<String, Object> area) {
        boolean temCoordenada() {
            return lat != null;
        }
    }

    private final DemandaService demandas;
    private final ReferenciaSateliteService referencia;
    private final JdbcTemplate jdbc;
    private final int janelaDias;
    private final CamadaPublicaService camadasPublicas;

    public AnaliseEspacial(DemandaService demandas, ReferenciaSateliteService referencia, JdbcTemplate jdbc,
                           int janelaDias) {
        this(demandas, referencia, jdbc, janelaDias, null);
    }

    @Autowired
    public AnaliseEspacial(DemandaService demandas, ReferenciaSateliteService referencia, JdbcTemplate jdbc,
                           @Value("${sema.triagem.espacial.janela-dias:90}") int janelaDias,
                           @Nullable CamadaPublicaService camadasPublicas) {
        this.janelaDias = janelaDias;
        this.demandas = demandas;
        this.referencia = referencia;
        this.jdbc = jdbc;
        this.camadasPublicas = camadasPublicas;
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
            return new Ponto("COORDENADA", d.latitude().doubleValue(), d.longitude().doubleValue(), null);
        }
        double[] c = Geodesia.centroide(d.areaGeometria());
        return c == null ? new Ponto("MUNICIPIO", null, null, null) : new Ponto("AREA", c[0], c[1], d.areaGeometria());
    }

    public EvidenciaEspacial analisar(UsuarioAutenticado ator, DemandaDetalhe d, Double raioKm) {
        double raio = raio(raioKm);
        Ponto p = ponto(d);
        List<Proxima> proximas = proximas(ator, d, raio);
        return new EvidenciaEspacial(p.origem(), p.lat(), p.lon(), raio, inpe(d, raio), proximas,
                documentos(d, proximas), camadasPublicas(d, p, raio));
    }

    /** Consulta sobreposição/proximidade com camadas públicas abertas (UCs, Terras Indígenas e hidrografia). */
    public SobreposicaoCamadasPublicas camadasPublicas(DemandaDetalhe d, double raio) {
        return camadasPublicas(d, ponto(d), raio);
    }

    private SobreposicaoCamadasPublicas camadasPublicas(DemandaDetalhe d, Ponto p, double raio) {
        if (camadasPublicas == null || !p.temCoordenada()) {
            return null;
        }
        if (d.camadasPublicas() != null && Double.compare(raio, CamadaPublicaService.RAIO_PADRAO_KM) == 0) {
            return d.camadasPublicas();
        }
        try {
            return camadasPublicas.sobreposicao(p.area(), p.lat(), p.lon(), raio);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // -------------------------------------------------------------------------------------------- INPE

    /**
     * Focos de calor e alertas DETER no raio, nos {@code janelaDias} dias até a data de recebimento da demanda.
     * Com coordenada o raio parte do ponto; com área, da borda da área. Focos: os mais próximos do centro (até
     * {@link #LIMITE_FOCOS}); DETER: candidatos por caixa/distância do centroide e só então a geometria é lida.
     */
    public Inpe inpe(DemandaDetalhe d, double raio) {
        long focosMun = contarMunicipio(TipoReferencia.FOCO_CALOR, d.municipio().id());
        long deterMun = contarMunicipio(TipoReferencia.DESMATAMENTO, d.municipio().id());
        Ponto p = ponto(d);
        if (!p.temCoordenada()) {
            return new Inpe(0, null, 0, false, null, focosMun, deterMun, janelaDias, false);
        }
        try {
            LocalDateTime ate = d.dataRecebimento().plusDays(1).atStartOfDay();
            LocalDateTime de = d.dataRecebimento().minusDays(janelaDias).atStartOfDay();
            boolean truncado = false;
            var c = p.area() != null ? Geodesia.caixaDaGeometria(p.area(), raio) : Geodesia.caixa(p.lat(), p.lon(), raio, 0);
            long focos = 0;
            Double maisProximo = null;
            List<PontoEspacial> pf = referencia.maisProximosNaCaixa(TipoReferencia.FOCO_CALOR, c.latMin(), c.latMax(),
                    c.lonMin(), c.lonMax(), de, ate, p.lat(), p.lon(), LIMITE_FOCOS);
            double kmUltimo = 0;
            for (PontoEspacial f : pf) {
                double km = p.area() != null ? Geodesia.distanciaAoPoligonoKm(f.latitude(), f.longitude(), p.area())
                        : Geodesia.haversineKm(p.lat(), p.lon(), f.latitude(), f.longitude());
                kmUltimo = km;
                if (km <= raio) {
                    focos++;
                    maisProximo = maisProximo == null ? km : Math.min(maisProximo, km);
                }
            }
            // Ordenado do centro para fora: se o último lido ainda está no raio, podem faltar focos.
            truncado |= pf.size() >= LIMITE_FOCOS && kmUltimo <= raio;
            var cd = p.area() != null ? Geodesia.caixaDaGeometria(p.area(), raio + MARGEM_DETER_KM)
                    : Geodesia.caixa(p.lat(), p.lon(), raio, MARGEM_DETER_KM);
            long alertas = 0;
            boolean dentro = false;
            Double menor = null;
            List<PontoEspacial> pd = referencia.maisProximosNaCaixa(TipoReferencia.DESMATAMENTO, cd.latMin(),
                    cd.latMax(), cd.lonMin(), cd.lonMax(), de, ate, p.lat(), p.lon(), LIMITE_DETER);
            truncado |= pd.size() >= LIMITE_DETER;
            for (PontoEspacial a : pd) {
                if (p.area() == null && Geodesia.haversineKm(p.lat(), p.lon(), a.latitude(), a.longitude())
                        > raio + MARGEM_DETER_KM) {
                    continue; // longe demais para o polígono tocar o raio: não lê o CLOB
                }
                double km = distanciaDeter(p, a);
                if (km <= raio) {
                    alertas++;
                    dentro |= km == 0;
                    menor = menor == null ? km : Math.min(menor, km);
                }
            }
            return new Inpe(focos, arredondar(maisProximo), alertas, dentro, arredondar(menor), focosMun, deterMun,
                    janelaDias, truncado);
        } catch (RuntimeException e) {
            return new Inpe(0, null, 0, false, null, focosMun, deterMun, janelaDias, false);
        }
    }

    /** Distância do ponto (ou da área) ao polígono DETER; sem geometria válida usa a distância ao centroide. */
    private double distanciaDeter(Ponto p, PontoEspacial a) {
        Map<String, Object> g = GeoJson.geometria(referencia.geometria(a.id()));
        double km = g == null ? Double.NaN : p.area() != null ? Geodesia.distanciaEntreAreasKm(p.area(), g)
                : Geodesia.distanciaAoPoligonoKm(p.lat(), p.lon(), g);
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
                if (citam.size() > LIMITE_CITAM) {
                    citam = citam.subList(0, LIMITE_CITAM);
                }
                if (!citam.isEmpty()) {
                    jdbc.query("SELECT ID, TITULO FROM DOCUMENTO WHERE EXCLUIDO_EM IS NULL AND MUNICIPIO_ID = ? "
                                    + "AND ID IN (" + marcas(citam.size()) + ") FETCH FIRST ? ROWS ONLY",
                            (rs, i) -> r.putIfAbsent(rs.getLong("ID"), new DocumentoRelacionado(rs.getLong("ID"),
                                    rs.getString("TITULO"), "Cita a localidade \"" + loc.strip() + "\"")),
                            argumentos(d.municipio().id(), citam, MAX_DOCUMENTOS));
                }
            }
        } catch (RuntimeException e) {
            // consulta auxiliar: segue com as demais
        }
        try {
            List<Long> ids = proximas.stream().map(Proxima::id).toList();
            if (!ids.isEmpty()) {
                Map<Long, Double> distancia = new LinkedHashMap<>();
                Map<Long, String> numeros = new LinkedHashMap<>();
                proximas.forEach(x -> {
                    distancia.put(x.id(), x.distanciaKm());
                    numeros.put(x.id(), x.numero());
                });
                List<long[]> pares = new ArrayList<>();
                Map<Long, String> titulos = new LinkedHashMap<>();
                jdbc.query("SELECT DISTINCT doc.ID, doc.TITULO, dd.DEMANDA_ID FROM DOCUMENTO_DEMANDA dd "
                                + "JOIN DOCUMENTO doc ON doc.ID = dd.DOCUMENTO_ID WHERE doc.EXCLUIDO_EM IS NULL "
                                + "AND dd.DEMANDA_ID IN (" + marcas(ids.size()) + ") FETCH FIRST ? ROWS ONLY",
                        (rs, i) -> {
                            pares.add(new long[] {rs.getLong("ID"), rs.getLong("DEMANDA_ID")});
                            titulos.put(rs.getLong("ID"), rs.getString("TITULO"));
                            return null;
                        }, argumentos(null, ids, MAX_DOCUMENTOS * 4));
                pares.sort(java.util.Comparator.comparingDouble(
                        par -> distancia.get(par[1]) == null ? Double.MAX_VALUE : distancia.get(par[1])));
                for (long[] par : pares) {
                    Double km = distancia.get(par[1]);
                    r.putIfAbsent(par[0], new DocumentoRelacionado(par[0], titulos.get(par[0]),
                            "Vinculado a demanda " + numeros.get(par[1]) + (km == null ? "" : " (" + km + " km)")));
                }
            }
        } catch (RuntimeException e) {
            // consulta auxiliar: segue com as demais
        }
        try {
            jdbc.query("SELECT ID, TITULO FROM DOCUMENTO WHERE EXCLUIDO_EM IS NULL AND MUNICIPIO_ID = ? "
                            + "ORDER BY DATA_DOCUMENTO DESC FETCH FIRST ? ROWS ONLY",
                    (rs, i) -> r.putIfAbsent(rs.getLong("ID"), new DocumentoRelacionado(rs.getLong("ID"),
                            rs.getString("TITULO"), "Mesmo município (" + d.municipio().nome() + ")")),
                    d.municipio().id(), MAX_DOCUMENTOS);
        } catch (RuntimeException e) {
            // consulta auxiliar: devolve o que já foi reunido
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
