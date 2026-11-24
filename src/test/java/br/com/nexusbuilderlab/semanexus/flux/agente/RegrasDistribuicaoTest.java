package br.com.nexusbuilderlab.semanexus.flux.agente;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.Carga;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.Cargas;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.EquipeCandidata;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.Membro;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.TempoMedio;

import org.junit.jupiter.api.Test;

class RegrasDistribuicaoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 24);

    @Test
    void pontuacaoPesaAtrasadasEProximas() {
        assertThat(RegrasDistribuicao.pontos(new Carga(2, 1, 1))).isEqualTo(2 + 3 + 2);
    }

    @Test
    void escolheMenorCargaEPrefereFiscalDaEquipe() {
        EquipeCandidata a = new EquipeCandidata(1L, "A", "NR-CBA", "NR", List.of(
                new Membro(10L, "Gestor", Perfil.GESTOR), new Membro(11L, "Zeca", Perfil.FISCAL)));
        EquipeCandidata b = new EquipeCandidata(2L, "B", "NR-CBA", "NR", List.of(new Membro(20L, "Ana", Perfil.FISCAL)));
        Cargas cargas = new Cargas(Map.of(), Map.of(10L, new Carga(0, 0, 0), 11L, new Carga(1, 0, 0),
                20L, new Carga(1, 1, 0)));

        RegrasDistribuicao.Escolha e = RegrasDistribuicao.escolher(List.of(a, b), cargas, Set.of());

        // O gestor (carga 0) não é FISCAL: a equipe A tem fiscal, então só Zeca concorre por ela.
        assertThat(e.fiscal().id()).isEqualTo(11L);
        assertThat(e.equipe().id()).isEqualTo(1L);
    }

    @Test
    void empateDesempataPelaLogisticaENome() {
        EquipeCandidata a = new EquipeCandidata(1L, "A", null, null, List.of(new Membro(10L, "Ana", Perfil.FISCAL)));
        EquipeCandidata b = new EquipeCandidata(2L, "B", null, null, List.of(new Membro(20L, "Bia", Perfil.FISCAL)));
        Cargas cargas = new Cargas(Map.of(), Map.of(10L, new Carga(2, 0, 0), 20L, new Carga(2, 0, 0)));

        assertThat(RegrasDistribuicao.escolher(List.of(a, b), cargas, Set.of()).equipe().id()).isEqualTo(1L);
        RegrasDistribuicao.Escolha e = RegrasDistribuicao.escolher(List.of(a, b), cargas, Set.of(2L));
        assertThat(e.equipe().id()).isEqualTo(2L);
        assertThat(e.mesmoMunicipio()).isTrue();
    }

    @Test
    void semEquipeNaoHaEscolha() {
        assertThat(RegrasDistribuicao.escolher(List.of(), new Cargas(Map.of(), Map.of()), Set.of())).isNull();
    }

    @Test
    void prazoPelaMediaPrioridadeELimitesSempreAPartirDeHoje() {
        TempoMedio vinte = new TempoMedio(CategoriaFiscalizacao.DESMATAMENTO_ILEGAL, 5, 20.2);
        assertThat(RegrasDistribuicao.prazo(HOJE, vinte, Prioridade.MEDIA)).isEqualTo(HOJE.plusDays(21));
        assertThat(RegrasDistribuicao.prazo(HOJE, vinte, Prioridade.ALTA)).isEqualTo(HOJE.plusDays(15));
        assertThat(RegrasDistribuicao.prazo(HOJE, vinte, Prioridade.URGENTE)).isEqualTo(HOJE.plusDays(7));
        assertThat(RegrasDistribuicao.prazo(HOJE, new TempoMedio(null, 0, null), null))
                .isEqualTo(HOJE.plusDays(RegrasDistribuicao.PRAZO_PADRAO_DIAS));
        assertThat(RegrasDistribuicao.prazo(HOJE, new TempoMedio(null, 3, 0.0), null))
                .isEqualTo(HOJE.plusDays(RegrasDistribuicao.PRAZO_MINIMO_DIAS));
        assertThat(RegrasDistribuicao.prazo(HOJE, new TempoMedio(null, 3, 400.0), null))
                .isEqualTo(HOJE.plusDays(RegrasDistribuicao.PRAZO_MAXIMO_DIAS));
    }
}
