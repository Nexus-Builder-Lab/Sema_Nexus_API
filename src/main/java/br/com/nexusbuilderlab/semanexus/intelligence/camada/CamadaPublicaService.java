package br.com.nexusbuilderlab.semanexus.intelligence.camada;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.geo.GeoJson;
import br.com.nexusbuilderlab.semanexus.common.geo.Geodesia;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.FeatureCollection;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.FeatureGeoJson;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.FonteResumo;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.GeoJsonResponse;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.Importacao;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.ItemSobreposicao;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.Metadados;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.ResumoSobreposicao;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaDto.SobreposicaoCamadasPublicas;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaRepository.Feicao;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta somente leitura das camadas públicas abertas offline de MT (MMA/CNUC, FUNAI e ANA/SNIRH) e análise
 * espacial de sobreposição/proximidade. Nunca grava dados nem faz acesso de rede em tempo de execução.
 */
@Service
@Transactional(readOnly = true)
public class CamadaPublicaService {

    public static final int LIMITE_PADRAO = 5000;
    public static final int LIMITE_MAXIMO = 10000;
    public static final double RAIO_PADRAO_KM = 10.0;
    public static final double RAIO_MAXIMO_KM = 50.0;
    static final int LIMITE_CANDIDATOS_CAIXA = 2000;
    static final int MAX_ITENS_SOBREPOSICAO = 20;

    private final CamadaPublicaRepository repository;

    public CamadaPublicaService(CamadaPublicaRepository repository) {
        this.repository = repository;
    }

    public Metadados metadados() {
        List<Importacao> imp = repository.importacoes();
        return new Metadados(false, atualizadoEm(imp), CamadaPublicaDto.AVISO, imp);
    }

    public GeoJsonResponse geojson(TipoCamadaPublica camada, String esfera, Integer nivel, String q, Integer limite) {
        if (camada == null) {
            throw new DadosInvalidosException("camada", "Informe a camada pública desejada.");
        }
        if (nivel != null) {
            if (camada != TipoCamadaPublica.HIDROGRAFIA) {
                throw new DadosInvalidosException("nivel", "O filtro nivel aplica-se apenas à camada HIDROGRAFIA.");
            }
            if (nivel < 1 || nivel > 3) {
                throw new DadosInvalidosException("nivel", "O nível de hidrografia deve estar entre 1 e 3.");
            }
        }
        if (limite != null && limite < 1) {
            throw new DadosInvalidosException("limite", "O limite deve ser maior que zero.");
        }
        if (q != null && q.strip().length() > 100) {
            throw new DadosInvalidosException("q", "O termo de busca deve ter no máximo 100 caracteres.");
        }

        int efetivo = limite == null ? LIMITE_PADRAO : Math.min(limite, LIMITE_MAXIMO);
        List<Feicao> lidas = repository.feicoes(camada, esfera, nivel, q, efetivo + 1);
        boolean truncado = lidas.size() > efetivo;
        List<Feicao> usadas = truncado ? lidas.subList(0, efetivo) : lidas;

        List<Importacao> impTodas = repository.importacoes();
        Map<Long, Importacao> porId = new LinkedHashMap<>();
        Importacao daCamada = null;
        for (Importacao i : impTodas) {
            porId.put(i.id(), i);
            if (i.camada() == camada) {
                daCamada = i;
            }
        }

        List<FeatureGeoJson> features = new ArrayList<>(usadas.size());
        for (Feicao f : usadas) {
            Map<String, Object> geom = GeoJson.geometriaQualquer(f.geometria());
            if (geom == null) {
                continue;
            }
            Importacao imp = porId.get(f.importacaoId());
            Map<String, Object> props = new LinkedHashMap<>();
            Map<String, Object> originais = GeoJson.objeto(f.atributos());
            if (originais != null) {
                props.putAll(originais);
            }
            props.put("id", f.id());
            props.put("camada", f.camada().name());
            props.put("codigoOrigem", f.codigoOrigem());
            props.put("nome", f.nome());
            props.put("categoria", f.categoria());
            props.put("esfera", f.esfera());
            props.put("areaHa", f.areaHa());
            props.put("fonte", imp != null ? imp.fonte() : camada.rotulo());
            props.put("licenca", imp != null ? imp.licenca() : null);
            props.put("dataReferencia", imp != null ? imp.dataReferencia() : null);
            props.put("oficialSema", false);
            features.add(new FeatureGeoJson("Feature", f.id(), props, geom));
        }

        return new GeoJsonResponse(
                camada,
                daCamada != null ? daCamada.fonte() : camada.rotulo(),
                daCamada != null ? daCamada.licenca() : null,
                daCamada != null ? daCamada.dataReferencia() : null,
                daCamada != null ? daCamada.importadoEm() : atualizadoEm(impTodas),
                false,
                CamadaPublicaDto.AVISO,
                features.size(),
                truncado,
                new FeatureCollection("FeatureCollection", features));
    }

    /** Análise de sobreposição/proximidade para um ponto em WGS84. */
    public SobreposicaoCamadasPublicas sobreposicaoPonto(double lat, double lon, Double raioKm) {
        return sobreposicao(null, lat, lon, raioKm);
    }

    /** Análise de sobreposição/proximidade para uma demanda (usa área, se houver; senão coordenada). */
    public SobreposicaoCamadasPublicas sobreposicao(DemandaDetalhe d, Double raioKm) {
        if (d == null) {
            return sobreposicao(null, null, null, raioKm);
        }
        Double lat = d.latitude() == null ? null : d.latitude().doubleValue();
        Double lon = d.longitude() == null ? null : d.longitude().doubleValue();
        return sobreposicao(d.areaGeometria(), lat, lon, raioKm);
    }

    /**
     * Consulta de sobreposição e proximidade com UCs, Terras Indígenas e hidrografia principal de MT.
     * Quando {@code areaGeometria} é válida, calcula distância da área (0 km = interseção). Caso contrário usa o
     * ponto ({@code lat}, {@code lon}). Sem ponto nem área, retorna {@code referencia = "SEM_COORDENADA"}.
     */
    public SobreposicaoCamadasPublicas sobreposicao(Map<String, Object> areaGeometria, Double lat, Double lon,
                                                  Double raioKm) {
        double raio = raioEfetivo(raioKm);
        List<Importacao> importacoes = repository.importacoes();
        Map<Long, Importacao> porImportacao = new LinkedHashMap<>();
        List<FonteResumo> fontes = new ArrayList<>();
        for (Importacao i : importacoes) {
            porImportacao.put(i.id(), i);
            fontes.add(new FonteResumo(i.camada(), i.fonte(), i.licenca(), i.dataReferencia()));
        }

        boolean temArea = areaGeometria != null && !Geodesia.aneis(areaGeometria).isEmpty();
        boolean temPonto = lat != null && lon != null;
        if (!temArea && !temPonto) {
            return new SobreposicaoCamadasPublicas(false, CamadaPublicaDto.AVISO, "SEM_COORDENADA", null, null, raio,
                    List.of(), List.of(), List.of(),
                    new ResumoSobreposicao(false, null, false, null, null, 0, 0, 0, fontes));
        }

        String referencia = temArea ? "AREA" : "COORDENADA";
        double[] centro = temArea ? Geodesia.centroide(areaGeometria) : new double[] {lat, lon};
        double refLat = centro != null ? centro[0] : lat;
        double refLon = centro != null ? centro[1] : lon;
        Geodesia.Caixa caixa = temArea
                ? Geodesia.caixaDaGeometria(areaGeometria, raio)
                : Geodesia.caixa(lat, lon, raio, 0);
        if (caixa == null) {
            return new SobreposicaoCamadasPublicas(false, CamadaPublicaDto.AVISO, "SEM_COORDENADA", null, null, raio,
                    List.of(), List.of(), List.of(),
                    new ResumoSobreposicao(false, null, false, null, null, 0, 0, 0, fontes));
        }

        List<Feicao> candidatas = repository.naCaixa(caixa.latMin(), caixa.latMax(), caixa.lonMin(), caixa.lonMax(),
                LIMITE_CANDIDATOS_CAIXA);

        Map<String, ItemSobreposicao> ucs = new LinkedHashMap<>();
        Map<String, ItemSobreposicao> tis = new LinkedHashMap<>();
        Map<String, ItemSobreposicao> hidros = new LinkedHashMap<>();

        for (Feicao f : candidatas) {
            Map<String, Object> geom = GeoJson.geometriaQualquer(f.geometria());
            if (geom == null) {
                continue;
            }
            double dist = temArea
                    ? Geodesia.distanciaEntreGeometriasKm(areaGeometria, geom)
                    : Geodesia.distanciaPontoAGeometriaKm(lat, lon, geom);
            if (Double.isNaN(dist) || dist > raio) {
                continue;
            }
            double distArred = arredondar(dist);
            boolean dentro = dist == 0.0;
            Importacao imp = porImportacao.get(f.importacaoId());
            String fonte = imp != null ? imp.fonte() : f.camada().rotulo();
            String licenca = imp != null ? imp.licenca() : "";
            LocalDate dataRef = imp != null ? imp.dataReferencia() : null;

            ItemSobreposicao item = new ItemSobreposicao(
                    f.id(), f.camada(), f.codigoOrigem(), f.nome(), f.categoria(), f.esfera(), f.areaHa(),
                    dentro, distArred, fonte, licenca, dataRef, false);

            switch (f.camada()) {
                case UNIDADE_CONSERVACAO -> acumularMaisProximo(ucs, chaveEntidade(f), item);
                case TERRA_INDIGENA -> acumularMaisProximo(tis, chaveEntidade(f), item);
                case HIDROGRAFIA -> acumularMaisProximo(hidros,
                        f.nome().toLowerCase() + "|" + (f.esfera() == null ? "" : f.esfera()), item);
            }
        }

        List<ItemSobreposicao> listaUcs = ordenarELimitar(ucs);
        List<ItemSobreposicao> listaTis = ordenarELimitar(tis);
        List<ItemSobreposicao> listaHidros = ordenarELimitar(hidros);

        boolean dentroUc = listaUcs.stream().anyMatch(ItemSobreposicao::dentro);
        Double ucMaisProx = listaUcs.isEmpty() ? null : listaUcs.get(0).distanciaKm();
        boolean dentroTi = listaTis.stream().anyMatch(ItemSobreposicao::dentro);
        Double tiMaisProx = listaTis.isEmpty() ? null : listaTis.get(0).distanciaKm();
        Double rioMaisProx = listaHidros.isEmpty() ? null : listaHidros.get(0).distanciaKm();

        ResumoSobreposicao resumo = new ResumoSobreposicao(
                dentroUc, ucMaisProx, dentroTi, tiMaisProx, rioMaisProx,
                listaUcs.size(), listaTis.size(), listaHidros.size(), fontes);

        return new SobreposicaoCamadasPublicas(
                false, CamadaPublicaDto.AVISO, referencia, arredondar6(refLat), arredondar6(refLon), raio,
                listaUcs, listaTis, listaHidros, resumo);
    }

    private static String chaveEntidade(Feicao f) {
        return f.codigoOrigem() != null && !f.codigoOrigem().isBlank()
                ? f.codigoOrigem()
                : f.nome().toLowerCase();
    }

    private static void acumularMaisProximo(Map<String, ItemSobreposicao> mapa, String chave, ItemSobreposicao novo) {
        mapa.merge(chave, novo, (atual, cand) -> cand.distanciaKm() < atual.distanciaKm()
                || (cand.dentro() && !atual.dentro()) ? cand : atual);
    }

    private static List<ItemSobreposicao> ordenarELimitar(Map<String, ItemSobreposicao> mapa) {
        return mapa.values().stream()
                .sorted(Comparator.comparing(ItemSobreposicao::dentro).reversed()
                        .thenComparingDouble(ItemSobreposicao::distanciaKm)
                        .thenComparing(ItemSobreposicao::nome))
                .limit(MAX_ITENS_SOBREPOSICAO)
                .toList();
    }

    private static double raioEfetivo(Double raioKm) {
        if (raioKm == null || raioKm.isNaN() || raioKm <= 0) {
            return RAIO_PADRAO_KM;
        }
        return Math.min(raioKm, RAIO_MAXIMO_KM);
    }

    private static double arredondar(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double arredondar6(double v) {
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }

    private static LocalDateTime atualizadoEm(List<Importacao> imp) {
        return imp.stream().map(Importacao::importadoEm).max(Comparator.naturalOrder()).orElse(null);
    }
}
