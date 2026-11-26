package br.com.nexusbuilderlab.semanexus.intelligence.camada;

import br.com.nexusbuilderlab.semanexus.common.geo.GeoJson;
import br.com.nexusbuilderlab.semanexus.common.geo.Geodesia;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaRepository.Linha;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * Carrega os snapshots offline de camadas públicas abertas de Mato Grosso em
 * {@code src/main/resources/referencia/camadas/} (UCs MMA/CNUC, Terras Indígenas FUNAI e hidrografia principal ANA).
 * Zero acesso de rede em tempo de execução; idempotente pelo SHA-256 do arquivo {@code .geojson.gz}.
 */
@Service
public class CamadaPublicaLoader {

    private static final Logger log = LoggerFactory.getLogger(CamadaPublicaLoader.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Fonte(
            String arquivo,
            TipoCamadaPublica camada,
            String fonte,
            LocalDate dataReferencia,
            String urlOrigem,
            String licenca) {
    }

    public record Resultado(boolean importado, int registros, int descartados) {
    }

    private final CamadaPublicaRepository repository;

    public CamadaPublicaLoader(CamadaPublicaRepository repository) {
        this.repository = repository;
    }

    /** Importa todos os snapshots de {@code classpath:referencia/camadas/*.properties} ainda não gravados. */
    @Transactional
    public int carregarSnapshot() {
        int importados = 0;
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            for (Resource r : resolver.getResources("classpath:referencia/camadas/*.properties")) {
                Properties p = new Properties();
                try (InputStream in = r.getInputStream();
                     var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    p.load(reader);
                }
                Fonte fonte = new Fonte(
                        p.getProperty("arquivo"),
                        TipoCamadaPublica.valueOf(p.getProperty("camada")),
                        p.getProperty("fonte"),
                        LocalDate.parse(p.getProperty("data_referencia")),
                        p.getProperty("url_origem"),
                        p.getProperty("licenca"));
                Resource dados = resolver.getResource("classpath:referencia/camadas/" + fonte.arquivo());
                byte[] bytes;
                try (InputStream in = dados.getInputStream()) {
                    bytes = in.readAllBytes();
                }
                Resultado res = importar(fonte, bytes);
                if (res.importado()) {
                    importados++;
                    log.info("Camada pública {} importada ({}): {} feição(ões), {} descartada(s).",
                            fonte.camada(), fonte.arquivo(), res.registros(), res.descartados());
                } else {
                    log.info("Camada pública {} já importada (SHA-256 igual): {} — nada a fazer.",
                            fonte.camada(), fonte.arquivo());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return importados;
    }

    /** Importa um arquivo {@code .geojson.gz}; não grava nada se o SHA-256 já existir no banco. */
    @Transactional
    public Resultado importar(Fonte fonte, byte[] gz) {
        String sha = sha256(gz);
        if (repository.existeImportacao(sha)) {
            return new Resultado(false, 0, 0);
        }
        List<Linha> linhas = new ArrayList<>();
        int descartados = 0;
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            JsonNode raiz = JSON.readTree(in);
            for (JsonNode feature : raiz.path("features")) {
                Linha linha = converter(fonte.camada(), feature);
                if (linha == null) {
                    descartados++;
                } else {
                    linhas.add(linha);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (linhas.isEmpty()) {
            throw new IllegalStateException("Camada pública " + fonte.arquivo()
                    + ": nenhuma feição válida; o snapshot anterior foi mantido.");
        }
        int removidas = repository.removerImportacoes(fonte.camada());
        if (removidas > 0) {
            log.info("Camada pública {}: snapshot anterior substituído ({} importação(ões)).",
                    fonte.camada(), removidas);
        }
        repository.gravar(
                fonte.camada(),
                truncar(fonte.fonte(), 200),
                fonte.dataReferencia(),
                truncar(fonte.urlOrigem(), 500),
                truncar(fonte.licenca(), 500),
                truncar(fonte.arquivo(), 200),
                sha,
                linhas);
        return new Resultado(true, linhas.size(), descartados);
    }

    static Linha converter(TipoCamadaPublica camada, JsonNode feature) {
        JsonNode geomNode = feature.path("geometry");
        if (!geomNode.isObject()) {
            return null;
        }
        String geomStr = geomNode.toString();
        Map<String, Object> geom = GeoJson.geometriaQualquer(geomStr);
        if (geom == null) {
            return null;
        }
        Geodesia.Caixa caixa = Geodesia.caixaDeQualquerGeometria(geom, 0);
        double[] centro = Geodesia.centroideDeQualquerGeometria(geom);
        if (caixa == null || centro == null) {
            return null;
        }
        String tipoGeom = String.valueOf(geom.get("type"));
        JsonNode p = feature.path("properties");

        String codigoOrigem = null;
        String nome = null;
        String categoria = null;
        String esfera = null;
        BigDecimal areaHa = null;

        switch (camada) {
            case UNIDADE_CONSERVACAO -> {
                codigoOrigem = texto(p, "cd_cnuc");
                nome = texto(p, "nome_uc");
                categoria = texto(p, "categoria");
                esfera = texto(p, "esfera");
                areaHa = decimalNaoNegativo(texto(p, "ha_total"));
            }
            case TERRA_INDIGENA -> {
                codigoOrigem = texto(p, "terrai_codigo");
                nome = texto(p, "terrai_nome");
                categoria = texto(p, "fase_ti");
                esfera = "Federal (FUNAI)";
                areaHa = decimalNaoNegativo(texto(p, "superficie_perimetro_ha"));
            }
            case HIDROGRAFIA -> {
                codigoOrigem = texto(p, "cocursodag");
                nome = texto(p, "noriocomp");
                if (nome == null) {
                    nome = codigoOrigem != null ? "Curso d'água (" + codigoOrigem + ")" : "Curso d'água";
                }
                int nivel = p.path("nivel").asInt(0);
                categoria = "Curso d'água";
                esfera = nivel > 0 ? "Nível " + nivel : "ANA/SNIRH";
            }
        }
        if (nome == null) {
            return null;
        }
        return new Linha(
                camada,
                truncar(codigoOrigem, 80),
                truncar(nome, 300),
                truncar(categoria, 150),
                truncar(esfera, 80),
                areaHa,
                coord(centro[0]),
                coord(centro[1]),
                coord(caixa.latMin()),
                coord(caixa.latMax()),
                coord(caixa.lonMin()),
                coord(caixa.lonMax()),
                tipoGeom,
                p.isObject() ? p.toString() : null,
                geomStr);
    }

    private static String texto(JsonNode p, String campo) {
        JsonNode v = p.path(campo);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText().strip();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal decimalNaoNegativo(String s) {
        if (s == null) {
            return null;
        }
        try {
            String normalizado = s.contains(",") && !s.contains(".") ? s.replace(",", ".") : s;
            BigDecimal b = new BigDecimal(normalizado).setScale(2, RoundingMode.HALF_UP);
            return b.signum() < 0 ? null : b;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal coord(double v) {
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP);
    }

    private static String truncar(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    static String sha256(byte[] dados) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
