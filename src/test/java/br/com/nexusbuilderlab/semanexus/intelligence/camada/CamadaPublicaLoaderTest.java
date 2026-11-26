package br.com.nexusbuilderlab.semanexus.intelligence.camada;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaLoader.Fonte;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaLoader.Resultado;
import br.com.nexusbuilderlab.semanexus.intelligence.camada.CamadaPublicaRepository.Linha;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteLoader;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CamadaPublicaLoaderTest {

    CamadaPublicaRepository repo = mock(CamadaPublicaRepository.class);
    CamadaPublicaLoader loader = new CamadaPublicaLoader(repo);

    static byte[] gz(String texto) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(out)) {
            g.write(texto.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    static Fonte fonte(TipoCamadaPublica camada) {
        return new Fonte("teste.geojson.gz", camada, "Fonte Oficial", LocalDate.parse("2025-08-01"),
                "https://exemplo.gov.br", "Creative Commons Atribuição (CC BY)");
    }

    static final String UC_GEOJSON = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"cd_cnuc":"0000.51.0001","nome_uc":"Parque Estadual Teste",
               "esfera":"Estadual","categoria":"Parque","ha_total":"1234.50"},
               "geometry":{"type":"Polygon","coordinates":[[[-56.1,-15.6],[-56.0,-15.6],[-56.0,-15.5],[-56.1,-15.6]]]}},
              {"type":"Feature","properties":{"cd_cnuc":"0000.51.0002","nome_uc":"Sem geometria"},
               "geometry":{"type":"Point","coordinates":[-56.0,-15.5]}}
            ]}
            """;

    @Test
    @SuppressWarnings("unchecked")
    void importaFeicoesValidasCalculandoBboxECentroide() throws Exception {
        when(repo.existeImportacao(anyString())).thenReturn(false);

        Resultado r = loader.importar(fonte(TipoCamadaPublica.UNIDADE_CONSERVACAO), gz(UC_GEOJSON));

        assertThat(r.importado()).isTrue();
        assertThat(r.registros()).isEqualTo(1);
        assertThat(r.descartados()).isEqualTo(1); // Point não aceito
        ArgumentCaptor<List<Linha>> cap = ArgumentCaptor.forClass(List.class);
        verify(repo).gravar(eq(TipoCamadaPublica.UNIDADE_CONSERVACAO), anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), cap.capture());
        Linha l = cap.getValue().get(0);
        assertThat(l.codigoOrigem()).isEqualTo("0000.51.0001");
        assertThat(l.nome()).isEqualTo("Parque Estadual Teste");
        assertThat(l.esfera()).isEqualTo("Estadual");
        assertThat(l.categoria()).isEqualTo("Parque");
        assertThat(l.areaHa()).isEqualByComparingTo("1234.50");
        assertThat(l.latMin()).isEqualByComparingTo("-15.600000");
        assertThat(l.latMax()).isEqualByComparingTo("-15.500000");
        assertThat(l.tipoGeometria()).isEqualTo("Polygon");
    }

    @Test
    void importacaoRepetidaComMesmoSha256EhIdempotente() throws Exception {
        byte[] gz = gz(UC_GEOJSON);
        when(repo.existeImportacao(CamadaPublicaLoader.sha256(gz))).thenReturn(true);

        Resultado r = loader.importar(fonte(TipoCamadaPublica.UNIDADE_CONSERVACAO), gz);

        assertThat(r.importado()).isFalse();
        assertThat(r.registros()).isZero();
        verify(repo, never()).removerImportacoes(any());
        verify(repo, never()).gravar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void snapshotNovoSubstituiAnteriorDaMesmaCamada() throws Exception {
        when(repo.existeImportacao(anyString())).thenReturn(false);
        when(repo.removerImportacoes(TipoCamadaPublica.UNIDADE_CONSERVACAO)).thenReturn(1);

        loader.importar(fonte(TipoCamadaPublica.UNIDADE_CONSERVACAO), gz(UC_GEOJSON));

        var ordem = org.mockito.Mockito.inOrder(repo);
        ordem.verify(repo).removerImportacoes(TipoCamadaPublica.UNIDADE_CONSERVACAO);
        ordem.verify(repo).gravar(eq(TipoCamadaPublica.UNIDADE_CONSERVACAO), anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    void snapshotVazioNaoApagaImportacaoAnterior() throws Exception {
        when(repo.existeImportacao(anyString())).thenReturn(false);

        assertThatThrownBy(() -> loader.importar(fonte(TipoCamadaPublica.HIDROGRAFIA), gz("{\"features\":[]}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("snapshot anterior foi mantido");
        verify(repo, never()).removerImportacoes(any());
        verify(repo, never()).gravar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshotsVersionadosOfflineDasTresCamadasCarregamComFonteELicencaSemRede() throws Exception {
        when(repo.existeImportacao(anyString())).thenReturn(false);

        int importados = loader.carregarSnapshot();

        assertThat(importados).isEqualTo(3);

        ArgumentCaptor<TipoCamadaPublica> camCap = ArgumentCaptor.forClass(TipoCamadaPublica.class);
        ArgumentCaptor<String> fonteCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> licencaCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<Linha>> linhasCap = ArgumentCaptor.forClass(List.class);
        verify(repo, org.mockito.Mockito.times(3)).gravar(camCap.capture(), fonteCap.capture(), any(), anyString(),
                licencaCap.capture(), anyString(), anyString(), linhasCap.capture());

        assertThat(camCap.getAllValues()).containsExactlyInAnyOrder(
                TipoCamadaPublica.UNIDADE_CONSERVACAO,
                TipoCamadaPublica.TERRA_INDIGENA,
                TipoCamadaPublica.HIDROGRAFIA);

        for (int i = 0; i < 3; i++) {
            TipoCamadaPublica cam = camCap.getAllValues().get(i);
            List<Linha> linhas = linhasCap.getAllValues().get(i);
            String fonte = fonteCap.getAllValues().get(i);
            String licenca = licencaCap.getAllValues().get(i);
            switch (cam) {
                case UNIDADE_CONSERVACAO -> {
                    assertThat(fonte).contains("MMA").contains("CNUC");
                    assertThat(licenca).contains("Creative Commons");
                    assertThat(linhas.size()).isGreaterThanOrEqualTo(90);
                    assertThat(linhas).anyMatch(l -> "Federal".equals(l.esfera()));
                    assertThat(linhas).anyMatch(l -> "Estadual".equals(l.esfera()));
                }
                case TERRA_INDIGENA -> {
                    assertThat(fonte).contains("FUNAI");
                    assertThat(licenca).contains("FUNAI");
                    assertThat(linhas.size()).isGreaterThanOrEqualTo(70);
                }
                case HIDROGRAFIA -> {
                    assertThat(fonte).contains("ANA");
                    assertThat(licenca).contains("ANA");
                    assertThat(linhas.size()).isGreaterThan(4000);
                    assertThat(linhas).anyMatch(l -> "Nível 1".equals(l.esfera()));
                    assertThat(linhas).anyMatch(l -> "Nível 2".equals(l.esfera()));
                    assertThat(linhas).anyMatch(l -> "Nível 3".equals(l.esfera()));
                }
            }
        }
    }

    @Test
    void metadadosPropertiesNaoColidemComLoaderDeSateliteInpe() throws Exception {
        ReferenciaSateliteRepository satRepo = mock(ReferenciaSateliteRepository.class);
        when(satRepo.existeImportacao(anyString())).thenReturn(true);
        ReferenciaSateliteLoader satLoader = new ReferenciaSateliteLoader(satRepo, mock(MunicipioRepository.class));

        // Não lança IllegalArgumentException ao varrer classpath:referencia/*.properties
        satLoader.carregarSnapshot();

        Properties p = new Properties();
        try (var in = getClass().getResourceAsStream("/referencia/camadas/ucs_mt.properties")) {
            assertThat(in).isNotNull();
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertThat(p.getProperty("camada")).isEqualTo("UNIDADE_CONSERVACAO");
        assertThat(p.getProperty("licenca")).contains("Creative Commons");
    }
}
