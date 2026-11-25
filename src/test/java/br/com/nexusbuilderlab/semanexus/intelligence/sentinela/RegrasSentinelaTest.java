package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.BriefingSentinela.AcaoSugerida;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.BriefingSentinela.Anomalia;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.RegrasSentinela.Agrupamento;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.RegrasSentinela.CargaEquipe;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.RegrasSentinela.ContagemInpe;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.RegrasSentinela.ItemFila;

import org.junit.jupiter.api.Test;

/** Regras determinísticas da Sentinela (Issue #138) com fixtures, sem banco. */
class RegrasSentinelaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 24);
    /** janela 30 d, mínimo INPE 10, fila 7 dias/mínimo 3, limites 4 ativas e 1 atrasada por fiscal. */
    private static final ParametrosSentinela P = ParametrosSentinela.PADRAO;
    private static final LocalDate DE = LocalDate.of(2026, 9, 30);
    private static final LocalDate ATE = LocalDate.of(2026, 10, 30);

    // ------------------------------------------------------------------------------ (a) INPE sem demanda

    @Test
    void inpeSemDemandaSoMunicipiosAcimaDoMinimoESemDemandaAberta() {
        List<ContagemInpe> contagens = List.of(
                new ContagemInpe(5107909, "Sinop", 25, 7),          // 32 ≥ 30: ALTA, sem demanda
                new ContagemInpe(5103403, "Cuiabá", 40, 0),         // tem demanda aberta: fora
                new ContagemInpe(5106752, "Paranatinga", 3, 18),    // 21 ≥ 20: MEDIA, DETER > focos
                new ContagemInpe(5100250, "Alta Floresta", 6, 6),   // 12: BAIXA
                new ContagemInpe(5108402, "Várzea Grande", 9, 0));  // 9 < 10: fora
        Map<Integer, Long> abertas = Map.of(5103403, 2L, 5100250, 0L);

        List<Anomalia> r = RegrasSentinela.inpeSemDemanda(contagens, abertas, P, DE, ATE,
                id -> id == 5107909 ? new Agrupamento(-11.86, -55.5, 14) : null);

        assertThat(r).extracting(Anomalia::id).containsExactly("INPE_SEM_DEMANDA:5107909",
                "INPE_SEM_DEMANDA:5106752", "INPE_SEM_DEMANDA:5100250");
        assertThat(r).extracting(Anomalia::severidade).containsExactly("ALTA", "MEDIA", "BAIXA");
        Anomalia sinop = r.get(0);
        assertThat(sinop.numeros()).containsEntry("focos", 25L).containsEntry("deter", 7L)
                .containsEntry("total", 32L).containsEntry("registrosNoAgrupamento", 14L);
        AcaoSugerida mapa = sinop.acoes().get(0);
        assertThat(mapa.tipo()).isEqualTo("FILTRAR_MAPA");
        assertThat(mapa.parametros()).containsEntry("municipioId", 5107909).containsEntry("de", "2026-09-30")
                .containsEntry("ate", "2026-10-30");
        AcaoSugerida criar = sinop.acoes().get(1);
        assertThat(criar.tipo()).isEqualTo("CRIAR_DEMANDA_MONITORAMENTO");
        assertThat(criar.parametros()).containsEntry("origem", "MONITORAMENTO").containsEntry("municipioId", 5107909)
                .containsEntry("latitude", -11.86).containsEntry("longitude", -55.5)
                .containsEntry("categoria", "QUEIMADA_INCENDIO");
        assertThat(r.get(1).acoes().get(1).parametros()).containsEntry("categoria", "DESMATAMENTO_ILEGAL")
                .containsEntry("latitude", null);
    }

    @Test
    void inpeSemDemandaRespeitaOLimitePorTipo() {
        ParametrosSentinela p = new ParametrosSentinela(true, 10, 30, 10, 7, 3, 4, 1, 2);
        List<ContagemInpe> contagens = List.of(new ContagemInpe(1, "A", 10, 0), new ContagemInpe(2, "B", 30, 0),
                new ContagemInpe(3, "C", 20, 0));
        assertThat(RegrasSentinela.inpeSemDemanda(contagens, Map.of(), p, DE, ATE, id -> null))
                .extracting(Anomalia::id).containsExactly("INPE_SEM_DEMANDA:2", "INPE_SEM_DEMANDA:3");
    }

    @Test
    void agrupamentoMaisDensoEscolheACelulaComMaisPontos() {
        List<double[]> pontos = List.of(new double[] {-11.81, -55.51}, new double[] {-11.83, -55.53},
                new double[] {-11.85, -55.55}, new double[] {-12.50, -56.00});
        Agrupamento a = RegrasSentinela.agrupamentoMaisDenso(pontos);
        assertThat(a.registros()).isEqualTo(3);
        assertThat(a.latitude()).isEqualTo(-11.83);
        assertThat(a.longitude()).isEqualTo(-55.53);
        assertThat(RegrasSentinela.agrupamentoMaisDenso(List.of())).isNull();
    }

    // ------------------------------------------------------------------------------ (b) filas acumuladas

    @Test
    void filaAcumuladaContaSoAsParadasHaNDiasOuMais() {
        LocalDateTime t = HOJE.atTime(9, 0);
        List<ItemFila> itens = List.of(
                new ItemFila(1, "DEM-2026/0001", StatusDemanda.EM_TRIAGEM, t.minusDays(30)),
                new ItemFila(2, "DEM-2026/0002", StatusDemanda.EM_TRIAGEM, t.minusDays(8)),
                new ItemFila(3, "DEM-2026/0003", StatusDemanda.EM_TRIAGEM, t.minusDays(7)),
                new ItemFila(4, "DEM-2026/0004", StatusDemanda.EM_TRIAGEM, t.minusDays(2)),  // recente: não conta
                new ItemFila(5, "DEM-2026/0005", StatusDemanda.AGUARDANDO_DISTRIBUICAO, t.minusDays(20)),
                new ItemFila(6, "DEM-2026/0006", StatusDemanda.AGUARDANDO_DISTRIBUICAO, t.minusDays(20)));

        List<Anomalia> r = RegrasSentinela.filasAcumuladas(itens, HOJE, P);

        assertThat(r).hasSize(1); // distribuição: só 2 paradas (< 3)
        Anomalia triagem = r.get(0);
        assertThat(triagem.id()).isEqualTo("FILA_ACUMULADA:EM_TRIAGEM");
        assertThat(triagem.severidade()).isEqualTo("BAIXA");
        assertThat(triagem.numeros()).containsEntry("noStatus", 4).containsEntry("paradas", 3)
                .containsEntry("diasMaisAntiga", 30L).containsEntry("maisAntiga", "DEM-2026/0001");
        assertThat(triagem.acoes()).extracting(AcaoSugerida::tipo).containsExactly("FILTRAR_DEMANDAS", "FILTRAR_MAPA");
        assertThat(triagem.acoes().get(0).parametros()).containsEntry("status", "EM_TRIAGEM");
    }

    @Test
    void filaComTriploDoMinimoEAlta() {
        LocalDateTime antiga = HOJE.minusDays(10).atStartOfDay();
        List<ItemFila> itens = java.util.stream.LongStream.rangeClosed(1, 9)
                .mapToObj(i -> new ItemFila(i, "DEM-" + i, StatusDemanda.PENDENTE, antiga)).toList();
        assertThat(RegrasSentinela.filasAcumuladas(itens, HOJE, P)).singleElement()
                .extracting(Anomalia::severidade).isEqualTo("ALTA");
    }

    // ------------------------------------------------------------------------------ (c) equipes sobrecarregadas

    @Test
    void equipeSobrecarregadaPorAtivasOuAtrasadasPorFiscal() {
        List<CargaEquipe> cargas = List.of(
                new CargaEquipe(1, "Equipe A", "NR-SNO", 2, 12, 4),  // 6 e 2 por fiscal: os dois → ALTA
                new CargaEquipe(2, "Equipe B", "NR-CBA", 2, 10, 0),  // 5 ativas por fiscal → MEDIA
                new CargaEquipe(3, "Equipe C", "NR-CBA", 3, 9, 3),   // 3 e 1: no limite, não estoura
                new CargaEquipe(4, "Equipe D", null, 0, 50, 50));    // sem fiscal: ignorada

        List<Anomalia> r = RegrasSentinela.equipesSobrecarregadas(cargas, P);

        assertThat(r).extracting(Anomalia::id).containsExactly("EQUIPE_SOBRECARREGADA:1", "EQUIPE_SOBRECARREGADA:2");
        assertThat(r).extracting(Anomalia::severidade).containsExactly("ALTA", "MEDIA");
        assertThat(r.get(0).numeros()).containsEntry("ativasPorFiscal", 6.0).containsEntry("atrasadasPorFiscal", 2.0);
        assertThat(r.get(0).titulo()).isEqualTo("Equipe A (NR-SNO) sobrecarregada");
        assertThat(r.get(0).acoes().get(0).parametros()).containsEntry("equipeId", 1L).containsEntry("atrasada", true);
    }

    @Test
    void ordenarPoeAltaPrimeiro() {
        Anomalia baixa = new Anomalia("x", "FILA_ACUMULADA", "BAIXA", "", "", Map.of(), List.of());
        Anomalia alta = new Anomalia("y", "EQUIPE_SOBRECARREGADA", "ALTA", "", "", Map.of(), List.of());
        Anomalia media = new Anomalia("z", "INPE_SEM_DEMANDA", "MEDIA", "", "", Map.of(), List.of());
        assertThat(RegrasSentinela.ordenar(List.of(baixa, alta, media))).containsExactly(alta, media, baixa);
    }
}
