package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteRepository.Linha;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega o snapshot versionado de dados PÚBLICOS do INPE (focos de calor e alertas DETER) em
 * {@code src/main/resources/referencia/}. Idempotente por SHA-256 do arquivo; descarta pontos fora de Mato Grosso;
 * associa município (código IBGE da fonte, nome do município da fonte ou município de centroide mais próximo) e bioma.
 *
 * <p>Formatos: focos de calor em CSV com cabeçalho (as colunas além das obrigatórias — id, frp,
 * numero_dias_sem_chuva, precipitacao, risco_fogo — vão para {@code ATRIBUTOS}); alertas DETER em {@code .geojson.gz}
 * (polígono e todos os atributos da fonte, em {@code GEOMETRIA} e {@code ATRIBUTOS}) ou no CSV antigo de centroides.
 * Um snapshot novo de um tipo substitui o anterior do mesmo tipo. O INPE entrega o foco de calor como ponto, sem área
 * queimada: nenhuma área é calculada aqui.
 */
@Service
public class ReferenciaSateliteLoader {

    private static final Logger log = LoggerFactory.getLogger(ReferenciaSateliteLoader.class);

    /** Limites de Mato Grosso (os mesmos dos CHECKs das tabelas). */
    static final BigDecimal LAT_MIN = new BigDecimal("-18.1");
    static final BigDecimal LAT_MAX = new BigDecimal("-7.3");
    static final BigDecimal LON_MIN = new BigDecimal("-61.7");
    static final BigDecimal LON_MAX = new BigDecimal("-50.1");

    /** Valor usado pelo BDQueimadas para "sem dado" (ex.: risco_fogo = -999). */
    static final double SEM_DADO_INPE = -999d;

    /** Colunas obrigatórias do CSV de focos; as demais viram atributos. */
    static final List<String> COLUNAS_FOCO = List.of("lat", "lon", "data_hora_gmt", "satelite", "municipio_id",
            "bioma");

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Descrição de um arquivo do snapshot (conteúdo do {@code .properties} gerado pelo script). */
    public record Fonte(String arquivo, TipoReferencia tipo, String fonte, LocalDate periodoDe, LocalDate periodoAte,
                        String urlOrigem, String licenca) {
    }

    /** Resultado da importação de um arquivo. */
    public record Resultado(boolean importado, int registros, int descartados) {
    }

    /** Índices de municípios usados na associação ponto → município. */
    record Municipios(Map<Integer, Municipio> porId, Map<String, Municipio> porNome, List<Municipio> todos) {

        static Municipios de(List<Municipio> todos) {
            Map<Integer, Municipio> porId = new HashMap<>();
            Map<String, Municipio> porNome = new HashMap<>();
            for (Municipio m : todos) {
                porId.put(m.getId(), m);
                if (m.getNome() != null) {
                    porNome.put(normalizar(m.getNome()), m);
                }
            }
            return new Municipios(porId, porNome, todos);
        }
    }

    private final ReferenciaSateliteRepository repository;
    private final MunicipioRepository municipios;

    public ReferenciaSateliteLoader(ReferenciaSateliteRepository repository, MunicipioRepository municipios) {
        this.repository = repository;
        this.municipios = municipios;
    }

    /** Importa todos os arquivos do classpath {@code referencia/*.properties} ainda não importados. */
    @Transactional
    public int carregarSnapshot() {
        int importados = 0;
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            for (Resource r : resolver.getResources("classpath:referencia/*.properties")) {
                Properties p = new Properties();
                try (InputStream in = r.getInputStream();
                     var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    p.load(reader);
                }
                Fonte fonte = new Fonte(p.getProperty("arquivo"), TipoReferencia.valueOf(p.getProperty("tipo")),
                        p.getProperty("fonte"), LocalDate.parse(p.getProperty("periodo_de")),
                        LocalDate.parse(p.getProperty("periodo_ate")), p.getProperty("url_origem"),
                        p.getProperty("licenca"));
                Resource dados = resolver.getResource("classpath:referencia/" + fonte.arquivo());
                byte[] bytes;
                try (InputStream in = dados.getInputStream()) {
                    bytes = in.readAllBytes();
                }
                Resultado res = importar(fonte, bytes);
                if (res.importado()) {
                    importados++;
                    log.info("Referência INPE importada: {} — {} registro(s), {} descartado(s) por estar(em) fora de MT.",
                            fonte.arquivo(), res.registros(), res.descartados());
                } else {
                    log.info("Referência INPE já importada (SHA-256 igual): {} — nada a fazer.", fonte.arquivo());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return importados;
    }

    /**
     * Importa um arquivo ({@code .csv.gz} ou {@code .geojson.gz}); não faz nada se o SHA-256 já foi importado.
     * Antes de gravar, remove as importações anteriores do mesmo tipo (o snapshot novo substitui o antigo).
     */
    public Resultado importar(Fonte fonte, byte[] gz) {
        String sha = sha256(gz);
        if (repository.existeImportacao(sha)) {
            return new Resultado(false, 0, 0);
        }
        Municipios mun = Municipios.de(municipios.findAll());
        List<Linha> linhas = new ArrayList<>();
        int[] descartados = {0};
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            if (fonte.arquivo() != null && fonte.arquivo().endsWith(".geojson.gz")) {
                deterGeoJson(fonte.arquivo(), JSON.readTree(in), mun, linhas, descartados);
            } else {
                csv(fonte, in, mun, linhas, descartados);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (descartados[0] > 0) {
            log.warn("Referência INPE {}: {} registro(s) descartado(s) (fora de MT ou inválidos).", fonte.arquivo(),
                    descartados[0]);
        }
        if (linhas.isEmpty()) {
            // Arquivo vazio ou em formato inesperado: não apaga o snapshot bom que já está no banco.
            throw new IllegalStateException("Referência INPE " + fonte.arquivo()
                    + ": nenhum registro válido; o snapshot anterior foi mantido.");
        }
        int substituidas = repository.removerImportacoes(fonte.tipo());
        if (substituidas > 0) {
            log.info("Referência INPE {}: snapshot anterior do tipo {} substituído ({} importação(ões)).",
                    fonte.arquivo(), fonte.tipo(), substituidas);
        }
        repository.gravar(fonte.fonte(), fonte.tipo(), fonte.periodoDe(), fonte.periodoAte(), fonte.urlOrigem(),
                fonte.licenca(), fonte.arquivo(), sha, linhas);
        return new Resultado(true, linhas.size(), descartados[0]);
    }

    private static void csv(Fonte fonte, InputStream in, Municipios mun, List<Linha> linhas, int[] descartados)
            throws IOException {
        var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String cab = reader.readLine();
        if (cab == null) {
            throw new IllegalArgumentException("Arquivo de referência vazio: " + fonte.arquivo());
        }
        List<String> colunas = List.of(cab.trim().split(",", -1));
        String linha;
        while ((linha = reader.readLine()) != null) {
            if (linha.isBlank()) {
                continue;
            }
            String[] c = linha.split(",", -1);
            Linha l = fonte.tipo() == TipoReferencia.FOCO_CALOR ? focoCalor(colunas, c, mun) : deter(c, mun);
            adicionar(fonte.arquivo(), linha, l, linhas, descartados);
        }
    }

    private static void adicionar(String arquivo, Object origem, Linha l, List<Linha> linhas, int[] descartados) {
        if (l == null) {
            descartados[0]++;
            if (descartados[0] <= 5) {
                log.debug("Referência INPE {}: registro descartado (fora de MT ou inválido): {}", arquivo, origem);
            }
        } else {
            linhas.add(l);
        }
    }

    /**
     * Foco de calor (BDQueimadas): colunas obrigatórias localizadas pelo nome no cabeçalho; as demais (id, frp,
     * numero_dias_sem_chuva, precipitacao, risco_fogo…) vão para os atributos. -999 (sem dado da fonte) é omitido.
     */
    static Linha focoCalor(List<String> colunas, String[] c, Municipios mun) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < colunas.size(); i++) {
            idx.put(colunas.get(i).trim(), i);
        }
        if (!idx.keySet().containsAll(COLUNAS_FOCO) || c.length < colunas.size()) {
            return null;
        }
        ObjectNode atr = JSON.createObjectNode();
        for (int i = 0; i < colunas.size(); i++) {
            String nome = colunas.get(i).trim();
            String valor = c[i].trim();
            if (COLUNAS_FOCO.contains(nome) || valor.isEmpty()) {
                continue;
            }
            try {
                BigDecimal numero = new BigDecimal(valor);
                if (numero.doubleValue() > SEM_DADO_INPE) {
                    atr.put(nome, numero);
                }
            } catch (NumberFormatException texto) {
                atr.put(nome, valor);
            }
        }
        return montar(TipoReferencia.FOCO_CALOR, c[idx.get("lat")], c[idx.get("lon")], c[idx.get("data_hora_gmt")],
                c[idx.get("satelite")], c[idx.get("municipio_id")], null, c[idx.get("bioma")], null, null,
                atr.isEmpty() ? null : atr.toString(), null, mun);
    }

    // CSV antigo de centroides do DETER: lat,lon,data,satelite,classe,area_km2,municipio_id,bioma
    private static Linha deter(String[] c, Municipios mun) {
        if (c.length < 8) {
            return null;
        }
        return montar(TipoReferencia.DESMATAMENTO, c[0], c[1], c[2], c[3], c[6], null, c[7], c[4], c[5], null, null,
                mun);
    }

    /** DETER no formato do script: {@code {"camadas":[{"bioma":"AMAZONIA","colecao":<FeatureCollection>}, ...]}}. */
    static void deterGeoJson(String arquivo, JsonNode raiz, Municipios mun, List<Linha> linhas, int[] descartados) {
        for (JsonNode camada : raiz.path("camadas")) {
            String bioma = camada.path("bioma").asText(null);
            for (JsonNode feature : camada.path("colecao").path("features")) {
                adicionar(arquivo, feature.path("id").asText(), deterFeature(feature, bioma, mun), linhas, descartados);
            }
        }
    }

    /**
     * Um alerta DETER: polígono da fonte (GeoJSON), centroide aproximado para o marcador (média dos vértices do 1º
     * anel) e área da fonte em km² — {@code areamunkm} (recorte por município), ver {@link #campoArea}.
     */
    static Linha deterFeature(JsonNode feature, String bioma, Municipios mun) {
        JsonNode geom = feature.path("geometry");
        JsonNode p = feature.path("properties");
        double[] centro = centroide(geom);
        if (centro == null) {
            return null;
        }
        String area = campoArea(p) == null ? null : texto(p, campoArea(p));
        return montar(TipoReferencia.DESMATAMENTO, Double.toString(centro[1]), Double.toString(centro[0]),
                texto(p, "view_date"), texto(p, "satellite"), texto(p, "mun_geocod"), texto(p, "municipality"),
                bioma, texto(p, "classname"), area, p.isObject() ? p.toString() : null, geom.toString(), mun);
    }

    /**
     * Campo de área (km²) da fonte usado no alerta DETER, ou {@code null} se a fonte não informou área.
     * {@code areamunkm} vem primeiro: é a área do recorte por município, o mesmo polígono desenhado no mapa.
     * No Cerrado, {@code areatotalkm} é a área do alerta inteiro (repetida em cada recorte) e só fica nos atributos.
     */
    static String campoArea(JsonNode propriedades) {
        for (String campo : List.of("areamunkm", "areatotalkm")) {
            if (texto(propriedades, campo) != null) {
                return campo;
            }
        }
        return null;
    }

    private static String texto(JsonNode p, String campo) {
        JsonNode v = p.path(campo);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    /** [lon, lat] médio dos vértices do 1º anel do 1º polígono; {@code null} se a geometria não for área. */
    static double[] centroide(JsonNode geom) {
        JsonNode coords = geom.path("coordinates");
        JsonNode anel = switch (geom.path("type").asText()) {
            case "Polygon" -> coords.path(0);
            case "MultiPolygon" -> coords.path(0).path(0);
            default -> null;
        };
        if (anel == null || !anel.isArray() || anel.isEmpty()) {
            return null;
        }
        double sx = 0;
        double sy = 0;
        int k = 0;
        for (JsonNode v : anel) {
            if (v.size() >= 2) {
                sx += v.get(0).asDouble();
                sy += v.get(1).asDouble();
                k++;
            }
        }
        return k == 0 ? null : new double[] {sx / k, sy / k};
    }

    private static Linha montar(TipoReferencia tipo, String lat, String lon, String data, String satelite,
                                String municipioId, String municipioNome, String bioma, String classe, String area,
                                String atributos, String geometria, Municipios mun) {
        try {
            BigDecimal la = new BigDecimal(lat.trim()).setScale(6, RoundingMode.HALF_UP);
            BigDecimal lo = new BigDecimal(lon.trim()).setScale(6, RoundingMode.HALF_UP);
            if (!dentroDeMt(la, lo)) {
                return null;
            }
            LocalDateTime dh = parseData(data.trim());
            Municipio m = null;
            if ((municipioId == null || municipioId.isBlank()) && municipioNome != null) {
                m = mun.porNome().get(normalizar(municipioNome));
            }
            if (m == null) {
                m = resolverMunicipio(municipioId, la, lo, mun.porId(), mun.todos());
            }
            if (m == null) {
                return null;
            }
            String b = normalizarBioma(bioma, m);
            BigDecimal a = area == null || area.isBlank() ? null
                    : new BigDecimal(area.trim()).setScale(4, RoundingMode.HALF_UP);
            return new Linha(tipo, dh, la, lo, m.getId(), b, vazioParaNulo(satelite), vazioParaNulo(classe), a,
                    atributos, geometria);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static boolean dentroDeMt(BigDecimal lat, BigDecimal lon) {
        return lat.compareTo(LAT_MIN) >= 0 && lat.compareTo(LAT_MAX) <= 0
                && lon.compareTo(LON_MIN) >= 0 && lon.compareTo(LON_MAX) <= 0;
    }

    static LocalDateTime parseData(String s) {
        return s.length() <= 10 ? LocalDate.parse(s).atStartOfDay() : LocalDateTime.parse(s.replace(' ', 'T'));
    }

    /** Código IBGE da fonte (se existir em MT) ou o município de centroide mais próximo. */
    static Municipio resolverMunicipio(String codigo, BigDecimal lat, BigDecimal lon, Map<Integer, Municipio> porId,
                                       List<Municipio> todos) {
        if (codigo != null && !codigo.isBlank()) {
            try {
                Municipio m = porId.get(Integer.parseInt(codigo.trim()));
                if (m != null) {
                    return m;
                }
            } catch (NumberFormatException ignorado) {
                // cai no centroide mais próximo
            }
        }
        Municipio melhor = null;
        double menor = Double.MAX_VALUE;
        double la = lat.doubleValue();
        double lo = lon.doubleValue();
        double cos = Math.cos(Math.toRadians(la));
        for (Municipio m : todos) {
            double dy = m.getLatitude().doubleValue() - la;
            double dx = (m.getLongitude().doubleValue() - lo) * cos;
            double d = dx * dx + dy * dy;
            if (d < menor) {
                menor = d;
                melhor = m;
            }
        }
        return melhor;
    }

    static String normalizarBioma(String fonte, Municipio municipio) {
        if (fonte != null && !fonte.isBlank()) {
            String n = normalizar(fonte);
            for (Bioma b : Bioma.values()) {
                if (b.name().equals(n)) {
                    return b.name();
                }
            }
        }
        return municipio.getBioma().name();
    }

    /** Sem acentos e em maiúsculas (comparação de nomes de município e bioma). */
    static String normalizar(String s) {
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase();
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String sha256(byte[] dados) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
