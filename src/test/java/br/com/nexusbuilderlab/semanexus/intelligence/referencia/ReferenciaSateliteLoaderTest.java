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
