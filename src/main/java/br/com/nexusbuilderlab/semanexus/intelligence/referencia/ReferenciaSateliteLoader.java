package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteRepository.Linha;
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
 * associa município (código IBGE da fonte ou município de centroide mais próximo) e bioma.
 */
@Service
public class ReferenciaSateliteLoader {

    private static final Logger log = LoggerFactory.getLogger(ReferenciaSateliteLoader.class);

    /** Limites de Mato Grosso (os mesmos dos CHECKs das tabelas). */
    static final BigDecimal LAT_MIN = new BigDecimal("-18.1");
    static final BigDecimal LAT_MAX = new BigDecimal("-7.3");
    static final BigDecimal LON_MIN = new BigDecimal("-61.7");
    static final BigDecimal LON_MAX = new BigDecimal("-50.1");

    /** Descrição de um arquivo do snapshot (conteúdo do {@code .properties} gerado pelo script). */
    public record Fonte(String arquivo, TipoReferencia tipo, String fonte, LocalDate periodoDe, LocalDate periodoAte,
                        String urlOrigem, String licenca) {
    }

    /** Resultado da importação de um arquivo. */
    public record Resultado(boolean importado, int registros, int descartados) {
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

    /** Importa um arquivo {@code .csv.gz}; não faz nada se o SHA-256 já foi importado. */
    public Resultado importar(Fonte fonte, byte[] gz) {
        String sha = sha256(gz);
        if (repository.existeImportacao(sha)) {
            return new Resultado(false, 0, 0);
        }
        Map<Integer, Municipio> porId = new HashMap<>();
        List<Municipio> todos = municipios.findAll();
        todos.forEach(m -> porId.put(m.getId(), m));

        List<Linha> linhas = new ArrayList<>();
        int descartados = 0;
        try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(gz)),
                StandardCharsets.UTF_8))) {
            String cab = reader.readLine();
            if (cab == null) {
                throw new IllegalArgumentException("Arquivo de referência vazio: " + fonte.arquivo());
            }
            String linha;
            while ((linha = reader.readLine()) != null) {
                if (linha.isBlank()) {
                    continue;
                }
                Linha l = fonte.tipo() == TipoReferencia.FOCO_CALOR
                        ? focoCalor(linha.split(",", -1), porId, todos)
                        : deter(linha.split(",", -1), porId, todos);
                if (l == null) {
                    descartados++;
                } else {
                    linhas.add(l);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        repository.gravar(fonte.fonte(), fonte.tipo(), fonte.periodoDe(), fonte.periodoAte(), fonte.urlOrigem(),
                fonte.licenca(), fonte.arquivo(), sha, linhas);
        return new Resultado(true, linhas.size(), descartados);
    }

    // lat,lon,data_hora_gmt,satelite,municipio_id,bioma,frp
    private static Linha focoCalor(String[] c, Map<Integer, Municipio> porId, List<Municipio> todos) {
        if (c.length < 7) {
            return null;
        }
        String frp = c[6].trim();
        String atributos = frp.isEmpty() ? null : "{\"frp\":\"" + frp.replaceAll("[^0-9.\\-]", "") + "\"}";
        return montar(TipoReferencia.FOCO_CALOR, c[0], c[1], c[2], c[3], c[4], c[5], null, null, atributos, porId, todos);
    }

    // lat,lon,data,satelite,classe,area_km2,municipio_id,bioma
    private static Linha deter(String[] c, Map<Integer, Municipio> porId, List<Municipio> todos) {
        if (c.length < 8) {
            return null;
        }
        return montar(TipoReferencia.DESMATAMENTO, c[0], c[1], c[2], c[3], c[6], c[7], c[4], c[5], null, porId, todos);
    }

    private static Linha montar(TipoReferencia tipo, String lat, String lon, String data, String satelite,
                                String municipioId, String bioma, String classe, String area, String atributos,
                                Map<Integer, Municipio> porId, List<Municipio> todos) {
        try {
            BigDecimal la = new BigDecimal(lat.trim()).setScale(6, RoundingMode.HALF_UP);
            BigDecimal lo = new BigDecimal(lon.trim()).setScale(6, RoundingMode.HALF_UP);
            if (!dentroDeMt(la, lo)) {
                return null;
            }
            LocalDateTime dh = parseData(data.trim());
            Municipio m = resolverMunicipio(municipioId, la, lo, porId, todos);
            if (m == null) {
                return null;
            }
            String b = normalizarBioma(bioma, m);
            BigDecimal a = area == null || area.isBlank() ? null : new BigDecimal(area.trim()).setScale(4, RoundingMode.HALF_UP);
            return new Linha(tipo, dh, la, lo, m.getId(), b, vazioParaNulo(satelite), vazioParaNulo(classe), a,
                    atributos);
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
            String n = Normalizer.normalize(fonte.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toUpperCase();
            for (Bioma b : Bioma.values()) {
                if (b.name().equals(n)) {
                    return b.name();
                }
            }
        }
        return municipio.getBioma().name();
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
