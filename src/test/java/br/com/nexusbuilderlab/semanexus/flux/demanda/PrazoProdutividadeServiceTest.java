package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.PrazoDtos.EstadoPrazo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.PrazoDtos.PrazoItem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.PrazoDtos.PrazoResumo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ProdutividadeResposta;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

/** Cálculos de prazo e produtividade (Issue #84) sobre um conjunto fixo de demandas fictícias. */
class PrazoProdutividadeServiceTest {

    static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC);
    static final LocalDate HOJE = LocalDate.of(2026, 11, 11);

    DemandaRepository repo = mock(DemandaRepository.class);
    EquipeRepository equipes = mock(EquipeRepository.class);
    PrazoService prazos = new PrazoService(repo, equipes, RELOGIO);
    ProdutividadeService produtividade = new ProdutividadeService(repo, equipes, RELOGIO);
    UsuarioAutenticado gestor = new UsuarioAutenticado(1L, "Gestor", "g@x.test", Perfil.GESTOR);

    Setor setorA;
    Setor setorB;
    Usuario ana;
    Usuario bruno;
    Municipio cuiaba;
    long seq;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        setorA = setor(1, "Setor A");
        setorB = setor(2, "Setor B");
        ana = usuario(10, "Ana");
        bruno = usuario(11, "Bruno");
        cuiaba = mock(Municipio.class);
        when(cuiaba.getNome()).thenReturn("Cuiabá");

        List<Demanda> todas = new ArrayList<>();
        // vencida há 10 dias (a mais antiga), ativa, setor A, Ana
        todas.add(d(StatusDemanda.EM_ANDAMENTO, "2026-10-01", HOJE.minusDays(10), null, setorA, ana));
        // vencida há 3 dias, setor B, Bruno
        todas.add(d(StatusDemanda.DISTRIBUIDA, "2026-10-05", HOJE.minusDays(3), null, setorB, bruno));
        // próxima (hoje+5, limite inclusivo), setor A, Ana
        todas.add(d(StatusDemanda.EM_ANDAMENTO, "2026-11-01", HOJE.plusDays(5), null, setorA, ana));
        // no prazo (hoje+6)
        todas.add(d(StatusDemanda.DISTRIBUIDA, "2026-11-02", HOJE.plusDays(6), null, setorB, bruno));
        // concluída no prazo: recebida 01/10, concluída 11/10 (10 dias), prazo 15/10; setor A, Ana
        todas.add(d(StatusDemanda.FINALIZADA, "2026-10-01", LocalDate.of(2026, 10, 15), "2026-10-11T10:00",
                setorA, ana));
        // concluída com atraso: recebida 01/10, concluída 21/10 (20 dias), prazo 15/10 (6 dias de atraso); setor B
        todas.add(d(StatusDemanda.FINALIZADA, "2026-10-01", LocalDate.of(2026, 10, 15), "2026-10-21T10:00",
                setorB, bruno));
        // sem prazo (ignorada pelo controle de prazos), sem equipe nem fiscal
        todas.add(d(StatusDemanda.PENDENTE, "2026-11-10", null, null, null, null));
        when(repo.findAll(any(Specification.class))).thenReturn(todas);
    }

    @Test
    void resumoContaPorEstadoELocalizaMaisAntigaVencida() {
        PrazoResumo r = prazos.resumo(gestor, null);

        assertThat(r.vencidas()).isEqualTo(2);
        assertThat(r.proximas()).isEqualTo(1);
        assertThat(r.noPrazo()).isEqualTo(1);
        assertThat(r.concluidasNoPrazo()).isEqualTo(1);
        assertThat(r.concluidasComAtraso()).isEqualTo(1);
        assertThat(r.maisAntigaVencida().diasAtraso()).isEqualTo(10);
        assertThat(r.demonstrativo()).isTrue();
    }

    @Test
    void diasAlertaAmpliaAJanelaDeProximas() {
        PrazoResumo r = prazos.resumo(gestor, 10);

        assertThat(r.proximas()).isEqualTo(2);
        assertThat(r.noPrazo()).isZero();
    }

    @Test
    void diasAlertaForaDoLimiteEhRejeitado() {
        assertThatThrownBy(() -> prazos.resumo(gestor, -1)).isInstanceOf(DadosInvalidosException.class);
    }

    @Test
    void listagemFiltraPorEstadoOrdenaPorPrazoECalculaDias() {
        PageResponse<PrazoItem> vencidas = prazos.listar(gestor, EstadoPrazo.VENCIDA, null, PageRequest.of(0, 10));

        assertThat(vencidas.totalElements()).isEqualTo(2);
        assertThat(vencidas.content()).extracting(PrazoItem::diasAtraso).containsExactly(10L, 3L);
        assertThat(vencidas.content().get(0).diasRestantes()).isNull();
        assertThat(vencidas.content().get(0).municipio()).isEqualTo("Cuiabá");
        assertThat(vencidas.content().get(0).fiscal()).isEqualTo("Ana");

        PageResponse<PrazoItem> proximas = prazos.listar(gestor, EstadoPrazo.PROXIMA, null, PageRequest.of(0, 10));
        assertThat(proximas.content()).singleElement().satisfies(i -> {
            assertThat(i.diasRestantes()).isEqualTo(5);
            assertThat(i.diasAtraso()).isNull();
        });

        PageResponse<PrazoItem> concluidas = prazos.listar(gestor, EstadoPrazo.CONCLUIDA, null, PageRequest.of(0, 10));
        assertThat(concluidas.content()).extracting(PrazoItem::diasAtraso).containsExactly(null, 6L);
    }

    @Test
    void listagemPagina() {
        PageResponse<PrazoItem> p = prazos.listar(gestor, null, null, PageRequest.of(1, 4));

        assertThat(p.totalElements()).isEqualTo(6);
        assertThat(p.totalPages()).isEqualTo(2);
        assertThat(p.content()).hasSize(2);
    }

    @Test
    void kpisDeProdutividade() {
        ProdutividadeResposta r = produtividade.calcular(gestor, null, null, null);

        assertThat(r.kpis().demandasNoPeriodo()).isEqualTo(7);
        assertThat(r.kpis().concluidas()).isEqualTo(2);
        assertThat(r.kpis().percentualConclusao()).isEqualByComparingTo("28.6");
        assertThat(r.kpis().tempoMedioConclusaoDias()).isEqualByComparingTo("15.0");
        assertThat(r.kpis().percentualConcluidasNoPrazo()).isEqualByComparingTo("50.0");
        assertThat(r.kpis().atrasadasAtivas()).isEqualTo(2);
        assertThat(r.demonstrativo()).isTrue();
    }

    @Test
    void agregacoesPorSetorMesEResponsavel() {
        ProdutividadeResposta r = produtividade.calcular(gestor, null, null, null);

        assertThat(r.porSetorSituacao()).extracting(ProdutividadeResposta.SetorSituacao::setor)
                .containsExactly("Sem setor", "Setor A", "Setor B");
        var a = r.porSetorSituacao().get(1);
        assertThat(a.total()).isEqualTo(3);
        assertThat(a.concluidas()).isEqualTo(1);
        assertThat(a.ativas()).isEqualTo(2);
        assertThat(a.atrasadas()).isEqualTo(1);

        assertThat(r.tempoMedioPorSetor()).extracting(ProdutividadeResposta.SetorTempoMedio::setor,
                t -> t.tempoMedioDias().doubleValue()).containsExactly(
                org.assertj.core.api.Assertions.tuple("Setor A", 10.0),
                org.assertj.core.api.Assertions.tuple("Setor B", 20.0));

        assertThat(r.historicoMensal()).extracting(ProdutividadeResposta.MesHistorico::mes,
                ProdutividadeResposta.MesHistorico::recebidas, ProdutividadeResposta.MesHistorico::concluidas)
                .containsExactly(org.assertj.core.api.Assertions.tuple("2026-10", 4L, 2L),
                        org.assertj.core.api.Assertions.tuple("2026-11", 3L, 0L));

        assertThat(r.porResponsavel()).hasSize(2);
        var ana = r.porResponsavel().get(0);
        assertThat(ana.fiscal()).isEqualTo("Ana");
        assertThat(ana.total()).isEqualTo(3);
        assertThat(ana.concluidas()).isEqualTo(1);
        assertThat(ana.ativas()).isEqualTo(2);
        assertThat(ana.atrasadas()).isEqualTo(1);
        assertThat(ana.tempoMedioDias()).isEqualByComparingTo("10.0");
        assertThat(ana.percentualConclusao()).isEqualByComparingTo("33.3");
    }

    @Test
    void filtroDeSetorRestringeORecorte() {
        ProdutividadeResposta r = produtividade.calcular(gestor, null, null, 2L);

        assertThat(r.kpis().demandasNoPeriodo()).isEqualTo(3);
        assertThat(r.kpis().concluidas()).isEqualTo(1);
        assertThat(r.kpis().tempoMedioConclusaoDias()).isEqualByComparingTo("20.0");
        assertThat(r.kpis().percentualConcluidasNoPrazo()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void periodoInvertidoEhRejeitado() {
        assertThatThrownBy(() -> produtividade.calcular(gestor, HOJE, HOJE.minusDays(1), null))
                .isInstanceOf(DadosInvalidosException.class);
    }

    @Test
    void semDemandasKpisSemBase() {
        ProdutividadeResposta r = ProdutividadeService.calcular(List.of(), HOJE);

        assertThat(r.kpis().demandasNoPeriodo()).isZero();
        assertThat(r.kpis().percentualConclusao()).isNull();
        assertThat(r.kpis().tempoMedioConclusaoDias()).isNull();
        assertThat(r.kpis().percentualConcluidasNoPrazo()).isNull();
        assertThat(r.historicoMensal()).isEmpty();
    }

    // ------------------------------------------------------------------ fábricas

    private Setor setor(long id, String nome) {
        Setor s = new Setor("S" + id, nome);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private Usuario usuario(long id, String nome) {
        Usuario u = new Usuario(nome, nome.toLowerCase() + "@exemplo.test", "hash", Perfil.FISCAL);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Demanda d(StatusDemanda status, String recebida, LocalDate prazo, String concluidaEm, Setor setor,
                      Usuario fiscal) {
        Demanda d = new Demanda("DEM-2026/" + String.format("%04d", ++seq), ana);
        ReflectionTestUtils.setField(d, "id", seq);
        d.setTitulo("Demanda " + seq);
        d.setStatus(status);
        d.setMunicipio(cuiaba);
        d.setPrioridade(Prioridade.MEDIA);
        d.setDataRecebimento(LocalDate.parse(recebida));
        d.setPrazo(prazo);
        if (concluidaEm != null) {
            d.setConcluidaEm(java.time.LocalDateTime.parse(concluidaEm));
        }
        if (setor != null) {
            d.setEquipe(new Equipe("Equipe " + setor.getNome(), setor));
        }
        d.setFiscal(fiscal);
        return d;
    }
}
