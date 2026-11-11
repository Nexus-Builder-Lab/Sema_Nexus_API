package br.com.nexusbuilderlab.semanexus.demo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nexusbuilderlab.semanexus.demo.DemoMassaGerador.DemandaSeed;
import br.com.nexusbuilderlab.semanexus.demo.DemoMassaGerador.MunicipioRef;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DemoMassaGeradorTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 11);

    private static List<MunicipioRef> municipios() {
        List<MunicipioRef> r = new ArrayList<>();
        for (int i = 0; i < DemoMassaGerador.MUNICIPIOS_IDS.size(); i++) {
            String bioma = i < 11 ? "AMAZONIA" : i < 21 ? "CERRADO" : "PANTANAL";
            r.add(new MunicipioRef(DemoMassaGerador.MUNICIPIOS_IDS.get(i), "Município " + i, bioma, -13.0 - i * 0.1,
                    -56.0 - i * 0.1));
        }
        return r;
    }

    @Test
    void geraVolumeStatusBiomasEMunicipios() {
        List<DemandaSeed> l = DemoMassaGerador.gerar(HOJE, municipios());
        assertThat(l).hasSize(120);
        assertThat(l.stream().map(DemandaSeed::status).collect(Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(DemoMassaGerador.STATUS_ORDEM);
        assertThat(l.stream().map(d -> d.municipio().bioma()).collect(Collectors.toSet())).hasSize(3);
        assertThat(l.stream().map(d -> d.municipio().id()).distinct().count()).isGreaterThanOrEqualTo(25);
    }

    @Test
    void ehDeterministicoParaOMesmoDia() {
        assertThat(DemoMassaGerador.gerar(HOJE, municipios())).isEqualTo(DemoMassaGerador.gerar(HOJE, municipios()));
    }

    @Test
    void datasRelativasHistoricoCoerenteEAtrasadas() {
        List<DemandaSeed> l = DemoMassaGerador.gerar(HOJE, municipios());
        assertThat(l).allSatisfy(d -> {
            assertThat(d.recebimento()).isBetween(HOJE.minusDays(365), HOJE);
            if (d.concluidaEm() != null) {
                assertThat(d.concluidaEm()).isBeforeOrEqualTo(HOJE).isAfter(d.recebimento());
            }
            assertThat(d.fiscalizacaoData() != null).isEqualTo(d.passou("AGUARDANDO_CONCLUSAO"));
            assertThat(d.prazo() != null).isEqualTo(d.passou("DISTRIBUIDA"));
            assertThat(d.latitude()).isBetween(-18.1, -7.3);
            assertThat(d.longitude()).isBetween(-61.7, -50.2);
        });
        assertThat(l.stream().filter(d -> d.prazo() != null && d.prazo().isBefore(HOJE)
                && !d.status().equals("FINALIZADA")).count()).isPositive();
    }

    @Test
    void queimadasConcentradasEmJulhoAOutubro() {
        List<DemandaSeed> l = DemoMassaGerador.gerar(HOJE, municipios());
        long seca = l.stream().filter(d -> d.categoria().equals("QUEIMADA_INCENDIO")
                && Set.of(7, 8, 9, 10).contains(d.recebimento().getMonthValue())).count();
        long fora = l.stream().filter(d -> d.categoria().equals("QUEIMADA_INCENDIO")).count() - seca;
        assertThat(seca).isGreaterThan(fora);
    }

    @Test
    void cenarioFixoPresenteComTitulosEstaveis() {
        List<DemandaSeed> l = DemoMassaGerador.gerar(HOJE, municipios());
        assertThat(l.stream().filter(d -> d.chave() != null).map(DemandaSeed::titulo).toList())
                .containsExactlyInAnyOrderElementsOf(DemoMassaGerador.CENARIOS.stream()
                        .map(DemoMassaGerador.Cenario::titulo).toList());
        assertThat(l).allSatisfy(d -> assertThat(d.descricao()).contains("FICTÍCIOS"));
    }
}
