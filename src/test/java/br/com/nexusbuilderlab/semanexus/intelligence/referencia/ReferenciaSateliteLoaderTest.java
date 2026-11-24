package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteLoader.Fonte;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteLoader.Resultado;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteRepository.Linha;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReferenciaSateliteLoaderTest {

    ReferenciaSateliteRepository repo = mock(ReferenciaSateliteRepository.class);
    MunicipioRepository municipios = mock(MunicipioRepository.class);
    ReferenciaSateliteLoader loader = new ReferenciaSateliteLoader(repo, municipios);

    @BeforeEach
    void setUp() {
        Municipio cuiaba = municipio(5103403, Bioma.CERRADO, "-15.601", "-56.097");
        Municipio sinop = municipio(5107909, Bioma.AMAZONIA, "-11.864", "-55.509");
        when(municipios.findAll()).thenReturn(List.of(cuiaba, sinop));
    }

    static Municipio municipio(int id, Bioma bioma, String lat, String lon) {
        Municipio m = mock(Municipio.class);
        when(m.getId()).thenReturn(id);
        when(m.getBioma()).thenReturn(bioma);
        when(m.getLatitude()).thenReturn(new BigDecimal(lat));
        when(m.getLongitude()).thenReturn(new BigDecimal(lon));
        return m;
    }

    static byte[] gz(String texto) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(out)) {
            g.write(texto.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    static Fonte fonte(TipoReferencia tipo) {
        return new Fonte("x.csv.gz", tipo, "INPE", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-11-01"),
                "https://exemplo", "CC BY-SA 4.0");
    }

    static final String FOCOS = "lat,lon,data_hora_gmt,satelite,municipio_id,bioma,frp\n"
            + "-15.6,-56.1,2026-10-01 12:00:00,AQUA_M-T,5103403,Cerrado,10.5\n"
            + "-11.9,-55.5,2026-10-02 03:00:00,NOAA-20,,Amazônia,\n"      // sem código: centroide mais próximo
            + "-23.5,-46.6,2026-10-02 03:00:00,NOAA-20,5103403,Cerrado,1.0\n"  // fora de MT: descartado
            + "-15.0,-45.0,2026-10-02 03:00:00,NOAA-20,5103403,Cerrado,1.0\n"; // fora de MT (longitude)

    @Test
    @SuppressWarnings("unchecked")
    void importaSomentePontosDentroDeMtComMunicipioEBioma() throws Exception {
        when(repo.existeImportacao(anyString())).thenReturn(false);

        Resultado r = loader.importar(fonte(TipoReferencia.FOCO_CALOR), gz(FOCOS));

        assertThat(r.importado()).isTrue();
        assertThat(r.registros()).isEqualTo(2);
        assertThat(r.descartados()).isEqualTo(2);
        ArgumentCaptor<List<Linha>> cap = ArgumentCaptor.forClass(List.class);
        verify(repo).gravar(anyString(), any(), any(), any(), anyString(), anyString(), anyString(), anyString(),
                cap.capture());
        List<Linha> linhas = cap.getValue();
        assertThat(linhas).extracting(Linha::municipioId).containsExactly(5103403, 5107909);
        assertThat(linhas).extracting(Linha::bioma).containsExactly("CERRADO", "AMAZONIA");
        assertThat(linhas.get(0).atributos()).contains("10.5");
    }

    @Test
    void importacaoRepetidaNaoGravaNada() throws Exception {
        byte[] arquivo = gz(FOCOS);
        when(repo.existeImportacao(ReferenciaSateliteLoader.sha256(arquivo))).thenReturn(true);

        Resultado r = loader.importar(fonte(TipoReferencia.FOCO_CALOR), arquivo);

        assertThat(r.importado()).isFalse();
        verify(repo, never()).gravar(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(municipios, never()).findAll();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deterTrazClasseAreaEMunicipioPeloCentroideQuandoSemCodigo() throws Exception {
        String csv = "lat,lon,data,satelite,classe,area_km2,municipio_id,bioma\n"
                + "-11.86,-55.50,2026-10-14,AMAZONIA-1,DESMATAMENTO_CR,0.2696,5107909,AMAZONIA\n"
                + "-15.60,-56.09,2026-10-15,CBERS-4,DESMATAMENTO_VEG,1.5,,CERRADO\n"
                + "-3.00,-56.09,2026-10-15,CBERS-4,DESMATAMENTO_VEG,1.5,,CERRADO\n";

        Resultado r = loader.importar(fonte(TipoReferencia.DESMATAMENTO), gz(csv));

        assertThat(r.registros()).isEqualTo(2);
        assertThat(r.descartados()).isEqualTo(1);
        ArgumentCaptor<List<Linha>> cap = ArgumentCaptor.forClass(List.class);
        verify(repo, times(1)).gravar(anyString(), any(), any(), any(), anyString(), anyString(), anyString(),
                anyString(), cap.capture());
        assertThat(cap.getValue()).extracting(Linha::municipioId).containsExactly(5107909, 5103403);
        assertThat(cap.getValue().get(0).classe()).isEqualTo("DESMATAMENTO_CR");
        assertThat(cap.getValue().get(0).areaKm2()).isEqualByComparingTo("0.2696");
    }

    /** Recorte do payload real do WFS do TerraBrasilis (deter-amz e deter-cerrado-nb, outputFormat=application/json). */
    static final String DETER_GEOJSON = """
            {"camadas":[
             {"bioma":"AMAZONIA","colecao":{"type":"FeatureCollection","features":[
              {"type":"Feature","id":"deter_amz.40570_curr","geometry":{"type":"MultiPolygon","coordinates":[[[
                [-55.52,-11.88],[-55.50,-11.88],[-55.50,-11.86],[-55.52,-11.86],[-55.52,-11.88]]]]},
               "geometry_name":"geom","properties":{"gid":"40570_curr","classname":"CICATRIZ_DE_QUEIMADA",
               "quadrant":null,"path_row":"036017","view_date":"2026-10-09","sensor":"WFI","satellite":"AMAZONIA-1",
               "areauckm":0,"uc":null,"areamunkm":0.13200473633075194,"municipality":"Sinop","mun_geocod":"5107909",
               "uf":"MT","publish_month":"2026-10-01"}},
              {"type":"Feature","id":"deter_amz.1_curr","geometry":{"type":"Point","coordinates":[-55.5,-11.8]},
               "properties":{"classname":"DESMATAMENTO_CR","view_date":"2026-10-09","areamunkm":1.0}}]}},
             {"bioma":"CERRADO","colecao":{"type":"FeatureCollection","features":[
              {"type":"Feature","id":"deter_cerrado.67090_curr","geometry":{"type":"MultiPolygon","coordinates":[[[
                [-56.11,-15.61],[-56.09,-15.61],[-56.09,-15.59],[-56.11,-15.61]]]]},
               "geometry_name":"st_multi","properties":{"gid":"67090_curr","classname":"DESMATAMENTO_CR",
               "quadrant":null,"path_row":"036_018","view_date":"2026-09-24","created_date":"2026-09-26",
               "sensor":"WFI","satellite":"AMAZONIA-1","areatotalkm":0.08763449378107364,"areauckm":null,"uc":null,
               "areamunkm":0.05,"municipality":"CUIABÁ","uf":"MT","publish_month":"2026-09-01"}}]}}]}
            """;

    static Fonte fonteGeoJson() {
        return new Fonte("deter_mt.geojson.gz", TipoReferencia.DESMATAMENTO, "INPE", LocalDate.parse("2026-09-13"),
                LocalDate.parse("2026-10-29"), "https://exemplo", "CC BY-SA 4.0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deterGeoJsonGuardaPoligonoAreaDaFonteEAtributos() throws Exception {
        Municipio cuiaba = municipio(5103403, Bioma.CERRADO, "-15.601", "-56.097");
        when(cuiaba.getNome()).thenReturn("Cuiabá");
        Municipio sinop = municipio(5107909, Bioma.AMAZONIA, "-11.864", "-55.509");
        when(municipios.findAll()).thenReturn(List.of(cuiaba, sinop));
        when(repo.existeImportacao(anyString())).thenReturn(false);

        Resultado r = loader.importar(fonteGeoJson(), gz(DETER_GEOJSON));

        assertThat(r.registros()).isEqualTo(2);
        assertThat(r.descartados()).isEqualTo(1); // geometria Point: não é área
        ArgumentCaptor<List<Linha>> cap = ArgumentCaptor.forClass(List.class);
        verify(repo).gravar(anyString(), any(), any(), any(), anyString(), anyString(), anyString(), anyString(),
                cap.capture());
        Linha amz = cap.getValue().get(0);
        assertThat(amz.tipo()).isEqualTo(TipoReferencia.DESMATAMENTO);
        assertThat(amz.municipioId()).isEqualTo(5107909);
        assertThat(amz.bioma()).isEqualTo("AMAZONIA");
        assertThat(amz.classe()).isEqualTo("CICATRIZ_DE_QUEIMADA");
        assertThat(amz.satelite()).isEqualTo("AMAZONIA-1");
        assertThat(amz.dataHora()).isEqualTo(LocalDate.parse("2026-10-09").atStartOfDay());
        // Amazônia não tem areatotalkm: usa areamunkm (km²), arredondada a 4 casas.
        assertThat(amz.areaKm2()).isEqualByComparingTo("0.1320");
        assertThat(amz.latitude()).isEqualByComparingTo("-11.872000");
        assertThat(amz.longitude()).isEqualByComparingTo("-55.512000");
        assertThat(amz.geometria()).startsWith("{\"type\":\"MultiPolygon\"").contains("-55.52");
        assertThat(amz.atributos()).contains("\"gid\":\"40570_curr\"", "\"path_row\":\"036017\"",
                "\"areamunkm\":0.13200473633075194", "\"municipality\":\"Sinop\"");

        Linha cer = cap.getValue().get(1);
        // Sem código IBGE na camada Cerrado: município pelo nome da fonte (sem acento/caixa).
        assertThat(cer.municipioId()).isEqualTo(5103403);
        assertThat(cer.bioma()).isEqualTo("CERRADO");
        // Cerrado tem areatotalkm: ela prevalece sobre areamunkm.
        assertThat(cer.areaKm2()).isEqualByComparingTo("0.0876");
        assertThat(cer.geometria()).contains("MultiPolygon");
    }

    @Test
    void snapshotNovoSubstituiOAnteriorDoMesmoTipo() throws Exception {
        when(repo.existeImportacao(anyString())).thenReturn(false);
        when(repo.removerImportacoes(TipoReferencia.FOCO_CALOR)).thenReturn(1);

        loader.importar(fonte(TipoReferencia.FOCO_CALOR), gz(FOCOS));

        var ordem = org.mockito.Mockito.inOrder(repo);
        ordem.verify(repo).removerImportacoes(TipoReferencia.FOCO_CALOR);
        ordem.verify(repo).gravar(anyString(), any(), any(), any(), anyString(), anyString(), anyString(),
                anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void focosGuardamTodosOsAtributosDaFonteSemAreaEOmitemSemDado() throws Exception {
        String csv = "id,lat,lon,data_hora_gmt,satelite,municipio_id,bioma,frp,numero_dias_sem_chuva,precipitacao,"
                + "risco_fogo\n"
                + "36311419-75fa-3bfa-bf3d-b6e5a652e011,-15.6,-56.1,2026-10-12 00:00:00,GOES-19,5103403,Cerrado,145.0,"
                + "3.0,3.3,0.82\n"
                + "7f8ead0c-2875-3bc8-9a7a-d12144c6d3da,-11.9,-55.5,2026-10-02 01:50:00,GOES-19,5107909,Amazônia,"
                + "100.7,9.0,1.84,-999.0\n";
        when(repo.existeImportacao(anyString())).thenReturn(false);

        loader.importar(fonte(TipoReferencia.FOCO_CALOR), gz(csv));

        ArgumentCaptor<List<Linha>> cap = ArgumentCaptor.forClass(List.class);
        verify(repo).gravar(anyString(), any(), any(), any(), anyString(), anyString(), anyString(), anyString(),
                cap.capture());
        Linha a = cap.getValue().get(0);
        assertThat(a.areaKm2()).isNull();
        assertThat(a.geometria()).isNull();
        assertThat(a.atributos()).contains("\"id\":\"36311419-75fa-3bfa-bf3d-b6e5a652e011\"", "\"frp\":145.0",
                "\"numero_dias_sem_chuva\":3.0", "\"precipitacao\":3.3", "\"risco_fogo\":0.82");
        assertThat(cap.getValue().get(1).atributos()).contains("\"frp\":100.7").doesNotContain("risco_fogo");
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshotVersionadoDoDeterTemPoligonoEAreaEmTodosOsAlertas() throws Exception {
        when(repo.existeImportacao(anyString())).thenReturn(false);
        byte[] arquivo;
        try (var in = getClass().getResourceAsStream("/referencia/deter_mt.geojson.gz")) {
            arquivo = in.readAllBytes();
        }

        Resultado r = loader.importar(fonteGeoJson(), arquivo);

        ArgumentCaptor<List<Linha>> cap = ArgumentCaptor.forClass(List.class);
        verify(repo).gravar(anyString(), any(), any(), any(), anyString(), anyString(), anyString(), anyString(),
                cap.capture());
        assertThat(r.registros()).isGreaterThan(1000);
        assertThat(cap.getValue()).allSatisfy(l -> {
            assertThat(l.geometria()).contains("Polygon");
            // A área vem da fonte em todos os alertas (alguns recortes por município têm menos de 0,0001 km²).
            assertThat(l.areaKm2()).isNotNull().isNotNegative();
            assertThat(l.atributos()).containsAnyOf("\"areatotalkm\":", "\"areamunkm\":");
        });
    }

    @Test
    void biomaInvalidoNaFonteUsaOBiomaDoMunicipio() {
        Municipio m = municipio(5107909, Bioma.AMAZONIA, "-11.864", "-55.509");
        assertThat(ReferenciaSateliteLoader.normalizarBioma("Amazônia", m)).isEqualTo("AMAZONIA");
        assertThat(ReferenciaSateliteLoader.normalizarBioma("Mata Atlântica", m)).isEqualTo("AMAZONIA");
        assertThat(ReferenciaSateliteLoader.normalizarBioma("", m)).isEqualTo("AMAZONIA");
    }

    @Test
    void limitesTerritoriaisDeMt() {
        assertThat(ReferenciaSateliteLoader.dentroDeMt(new BigDecimal("-15.6"), new BigDecimal("-56.1"))).isTrue();
        assertThat(ReferenciaSateliteLoader.dentroDeMt(new BigDecimal("-5.0"), new BigDecimal("-56.1"))).isFalse();
        assertThat(ReferenciaSateliteLoader.dentroDeMt(new BigDecimal("-15.6"), new BigDecimal("-49.0"))).isFalse();
    }
}
