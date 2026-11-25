package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import br.com.nexusbuilderlab.semanexus.agentes.AgentesConfiguracao;
import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo;
import br.com.nexusbuilderlab.semanexus.agentes.ExecutorAgente;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.Candidatas;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.Carga;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.Cargas;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.EquipeCandidata;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.Membro;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.TipoReferencia;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.ConsultasSentinela.Janela;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.RegrasSentinela.ContagemInpe;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.RegrasSentinela.ItemFila;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Serviço da Sentinela (Issue #138): modos (IA/template), validação dos números, janela do INPE e cache. */
class SentinelaTerritorialServiceTest {

    /** Relógio ajustável (cache). Começa em 24/11/2026 08:00 em Cuiabá. */
    static final class Relogio extends Clock {
        Instant agora = Instant.parse("2026-11-24T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(agora, zone);
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    /** Modelo com respostas roteirizadas. */
    static final class Roteiro implements ClienteModelo {
        final Deque<RespostaModelo> respostas = new ArrayDeque<>();
        boolean disponivel = true;

        @Override
        public boolean disponivel() {
            return disponivel;
        }

        @Override
        public String modelo(String sugerido) {
            return "qwen3:4b";
        }

        @Override
        public RespostaModelo criar(PedidoModelo pedido) {
            if (respostas.isEmpty()) {
                throw new IllegalStateException("fim do roteiro");
            }
            return respostas.poll();
        }
    }

    private static final LocalDateTime FIM_FOCOS = LocalDateTime.of(2026, 10, 30, 17, 45);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final ConsultasSentinela consultas = mock(ConsultasSentinela.class);
    private final ConsultasDistribuicao distribuicao = mock(ConsultasDistribuicao.class);
    private final AuditService auditoria = mock(AuditService.class);
    private final AgentesConfiguracao config = new AgentesConfiguracao("ollama", "", "claude-haiku-5-5",
            "claude-sonnet-5-5", "http://ollama:11434", "qwen3:4b", false, 30, 6);
    private final Relogio relogio = new Relogio();
    private final List<Runnable> tarefas = new ArrayList<>();

    private SentinelaTerritorialService service(Roteiro modelo) {
        return service(modelo, Runnable::run);
    }

    private SentinelaTerritorialService service(Roteiro modelo, Executor segundoPlano) {
        when(consultas.ultimaDataInpe(TipoReferencia.FOCO_CALOR)).thenReturn(Optional.of(FIM_FOCOS));
        when(consultas.ultimaDataInpe(TipoReferencia.DESMATAMENTO)).thenReturn(Optional.empty());
        when(consultas.inpePorMunicipio(anyList())).thenReturn(List.of(new ContagemInpe(5107909, "Sinop", 25, 0),
                new ContagemInpe(5103403, "Cuiabá", 12, 0)));
        when(consultas.abertasPorMunicipio()).thenReturn(Map.of(5103403, 3L));
        when(consultas.pontosInpe(eq(5107909), anyList())).thenReturn(List.of(new double[] {-11.85, -55.5}));
        when(consultas.itensFila()).thenReturn(fila());
        Map<String, Long> porStatus = new LinkedHashMap<>();
        porStatus.put("PENDENTE", 4L);
        porStatus.put("EM_TRIAGEM", 3L);
        when(consultas.abertasPorStatus()).thenReturn(porStatus);
        EquipeCandidata eq = new EquipeCandidata(1L, "Equipe A", "NR-SNO", "NR Sinop",
                List.of(new Membro(10L, "Ana", Perfil.FISCAL)));
        when(distribuicao.equipesCandidatas(null)).thenReturn(new Candidatas(List.of(eq), false));
        when(distribuicao.cargas(anyCollection(), any())).thenReturn(new Cargas(Map.of(1L, new Carga(6, 2, 0)),
                Map.of()));
        return new SentinelaTerritorialService(consultas, distribuicao,
                new ExecutorAgente(modelo, config, auditoria, json), config, ParametrosSentinela.PADRAO, json,
                relogio, segundoPlano);
    }

    private static List<ItemFila> fila() {
        LocalDateTime antiga = LocalDateTime.of(2026, 11, 1, 9, 0); // 23 dias antes de 24/11
        return List.of(new ItemFila(1, "DEM-2026/0001", StatusDemanda.EM_TRIAGEM, antiga),
                new ItemFila(2, "DEM-2026/0002", StatusDemanda.EM_TRIAGEM, antiga),
                new ItemFila(3, "DEM-2026/0003", StatusDemanda.EM_TRIAGEM, antiga));
    }

    private static Roteiro semIa() {
        Roteiro modelo = new Roteiro();
        modelo.disponivel = false;
        return modelo;
    }

    private ClienteModelo.RespostaModelo ferramentas() throws Exception {
        return new ClienteModelo.RespostaModelo("tool_calls", List.of(
                new ClienteModelo.ChamadaFerramenta("c1", "anomalias_sentinela", json.readTree("{\"tipo\":\"TODAS\"}")),
                new ClienteModelo.ChamadaFerramenta("c2", "indicadores_operacionais",
                        json.readTree("{\"escopo\":\"ESTADO\"}"))), "", null, 10, 5, 0);
    }

    private ClienteModelo.RespostaModelo texto(String resumo) throws Exception {
        return new ClienteModelo.RespostaModelo("stop", List.of(),
                json.writeValueAsString(Map.of("resumo", resumo)), null, 50, 30, 0);
    }

    /** Com IA: a 1ª consulta devolve o template (sem IA na thread HTTP) e a atualização em segundo plano, a IA. */
    private BriefingSentinela comIa(String resumo) throws Exception {
        Roteiro modelo = new Roteiro();
        modelo.respostas.add(ferramentas());
        modelo.respostas.add(texto(resumo));
        SentinelaTerritorialService s = service(modelo);
        BriefingSentinela primeiro = s.briefing();
        assertThat(primeiro.modo()).isEqualTo("DEMONSTRATIVO");
        assertThat(primeiro.avisos()).anyMatch(a -> a.contains("segundo plano"));
        return s.briefing();
    }

    @Test
    void semIaUsaTemplateIdentificadoComAsTresRegras() {
        BriefingSentinela b = service(semIa()).briefing();

        assertThat(b.modo()).isEqualTo("DEMONSTRATIVO");
        assertThat(b.modelo()).isNull();
        assertThat(b.aviso()).contains("AMBIENTE DEMONSTRATIVO");
        assertThat(b.anomalias()).extracting(BriefingSentinela.Anomalia::tipo)
                .containsExactlyInAnyOrder("INPE_SEM_DEMANDA", "FILA_ACUMULADA", "EQUIPE_SOBRECARREGADA");
        assertThat(b.anomalias().get(0).severidade()).isEqualTo("ALTA"); // equipe: 6 ativas e 2 atrasadas por fiscal
        assertThat(b.totais().anomalias()).isEqualTo(3);
        assertThat(b.indicadores().demandasAbertas()).isEqualTo(7);
        assertThat(b.indicadores().focosJanela()).isEqualTo(37);
        assertThat(b.resumo()).startsWith("Briefing de 24/11/2026: 3 anomalia(s)").contains("Há 7 demanda(s)");
        assertThat(b.origem()).isEqualTo("SOB_DEMANDA");
        assertThat(b.avisos()).isEmpty();
        verify(auditoria).registrar(eq("AGENTE_DEMONSTRATIVO"), eq("AGENTE"), eq("sentinela:briefing-2026-11-24"),
                anyString());
    }

    @Test
    void janelaDoInpeTerminaNoRegistroMaisRecenteDoPacote() {
        BriefingSentinela b = service(semIa()).briefing();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Janela>> janelas = ArgumentCaptor.forClass(List.class);
        verify(consultas).inpePorMunicipio(janelas.capture());
        assertThat(janelas.getValue()).singleElement().satisfies(j -> {
            assertThat(j.tipo()).isEqualTo(TipoReferencia.FOCO_CALOR);
            assertThat(j.ate()).isEqualTo(FIM_FOCOS);
            assertThat(j.de()).isEqualTo(FIM_FOCOS.minusDays(30));
        });
        assertThat(b.referenciaInpe().focosAte()).isEqualTo(FIM_FOCOS.toLocalDate());
        assertThat(b.referenciaInpe().deterAte()).isNull();
        BriefingSentinela.Anomalia sinop = b.anomalias().stream()
                .filter(a -> a.tipo().equals("INPE_SEM_DEMANDA")).findFirst().orElseThrow();
        assertThat(sinop.id()).isEqualTo("INPE_SEM_DEMANDA:5107909"); // Cuiabá tem demanda aberta
        assertThat(sinop.assunto()).isEqualTo("Sinop");
        assertThat(sinop.acoes().get(1).parametros()).containsEntry("origem", "MONITORAMENTO")
                .containsEntry("latitude", -11.85);
    }

    @Test
    void resumoDaIaComNumerosConferidosEUsado() throws Exception {
        BriefingSentinela b = comIa("Há 3 anomalias, 1 de severidade alta: a Equipe A tem 6 demandas ativas e 2 "
                + "atrasadas. Sinop teve 25 focos sem demanda aberta; sugere-se abrir demanda de monitoramento.");

        assertThat(b.modo()).isEqualTo("LOCAL");
        assertThat(b.modelo()).isEqualTo("qwen3:4b");
        assertThat(b.resumo()).startsWith("Há 3 anomalias");
        assertThat(b.avisos()).isEmpty();
        assertThat(b.evidencias()).anyMatch(e -> e.startsWith("IA: Consultou 3 anomalia"));
        verify(auditoria).registrar(eq("AGENTE_EXECUCAO"), eq("AGENTE"), anyString(), anyString());
    }

    @Test
    void numeroInventadoPelaIaCaiNoTemplate() throws Exception {
        BriefingSentinela b = comIa("Há 3 anomalias e 48 municípios em alerta crítico; Sinop teve 25 focos de "
                + "calor na janela recente.");

        assertThat(b.modo()).isEqualTo("DEMONSTRATIVO");
        assertThat(b.resumo()).startsWith("Briefing de 24/11/2026");
        assertThat(b.avisos()).singleElement().asString().contains("número ausente");
    }

    @Test
    void numeroDeOutraAnomaliaAtribuidoAoMunicipioCaiNoTemplate() throws Exception {
        // 23 existe (dias da fila mais antiga), mas não nos números da anomalia de Sinop.
        BriefingSentinela b = comIa("Há 3 anomalias no estado. Sinop teve 23 focos de calor sem demanda aberta e "
                + "precisa de monitoramento.");

        assertThat(b.modo()).isEqualTo("DEMONSTRATIVO");
        assertThat(b.avisos()).singleElement().asString().contains("número ausente");
    }

    @Test
    void urlsEMarcacaoSaemDoTextoDaIa() throws Exception {
        BriefingSentinela b = comIa("**Sinop** teve 25 focos sem demanda aberta; veja https://exemplo.test/1234. "
                + "Há 3 anomalias e a `Equipe A` está sobrecarregada.");

        assertThat(b.modo()).isEqualTo("LOCAL");
        assertThat(b.resumo()).doesNotContain("http", "*", "`", "1234").startsWith("Sinop teve 25 focos");
    }

    @Test
    void falhaDaIaCaiNoTemplateComAviso() {
        SentinelaTerritorialService s = service(new Roteiro()); // disponível, mas sem respostas: o executor falha
        s.briefing();
        BriefingSentinela b = s.briefing();
        assertThat(b.modo()).isEqualTo("DEMONSTRATIVO");
        assertThat(b.avisos()).anyMatch(a -> a.contains("IA"));
    }

    @Test
    void briefingVencidoEServidoNaHoraEAtualizadoUmaVezEmSegundoPlano() {
        SentinelaTerritorialService s = service(semIa(), tarefas::add);
        BriefingSentinela primeiro = s.briefing();
        assertThat(tarefas).isEmpty(); // sem IA: o template síncrono já é o briefing final

        relogio.agora = relogio.agora.plus(Duration.ofMinutes(9));
        assertThat(s.briefing()).isSameAs(primeiro);
        assertThat(tarefas).isEmpty();

        relogio.agora = relogio.agora.plus(Duration.ofMinutes(2)); // venceu
        assertThat(s.briefing()).isSameAs(primeiro); // servido na hora (stale)
        assertThat(s.briefing()).isSameAs(primeiro);
        assertThat(tarefas).hasSize(1); // uma única atualização em andamento
        verify(consultas, times(1)).itensFila();

        tarefas.remove(0).run();
        BriefingSentinela novo = s.briefing();
        assertThat(novo).isNotSameAs(primeiro);
        assertThat(novo.geradoEm()).isAfter(primeiro.geradoEm());
        verify(consultas, times(2)).itensFila();
    }

    @Test
    void requisicoesSimultaneasSemBriefingGeramUmaVez() throws Exception {
        SentinelaTerritorialService s = service(semIa());
        when(consultas.itensFila()).thenAnswer(i -> {
            Thread.sleep(150);
            return fila();
        });
        int n = 8;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<BriefingSentinela>> futuros = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return s.briefing();
                }));
            }
            largada.countDown();
            BriefingSentinela primeiro = futuros.get(0).get(5, TimeUnit.SECONDS);
            for (Future<BriefingSentinela> f : futuros) {
                assertThat(f.get(5, TimeUnit.SECONDS)).isSameAs(primeiro);
            }
        } finally {
            pool.shutdownNow();
        }
        verify(consultas, times(1)).itensFila();
    }

    @Test
    void falhaSemBriefingDa503EEsperaAntesDeTentarDeNovo() {
        SentinelaTerritorialService s = service(semIa());
        when(consultas.itensFila()).thenThrow(new IllegalStateException("banco fora"));

        assertThatThrownBy(s::briefing).isInstanceOf(SentinelaIndisponivelException.class);
        assertThatThrownBy(s::briefing).isInstanceOf(SentinelaIndisponivelException.class)
                .satisfies(e -> assertThat(((SentinelaIndisponivelException) e).retryAfterSegundos()).isPositive());
        verify(consultas, times(1)).itensFila(); // a 2ª não repetiu a geração

        relogio.agora = relogio.agora.plus(Duration.ofSeconds(61));
        assertThatThrownBy(s::briefing).isInstanceOf(SentinelaIndisponivelException.class);
        verify(consultas, times(2)).itensFila();
    }

    @Test
    void falhaNaAtualizacaoMantemOUltimoBriefingComAviso() {
        SentinelaTerritorialService s = service(semIa());
        BriefingSentinela bom = s.briefing();
        when(consultas.itensFila()).thenThrow(new IllegalStateException("banco fora"));

        relogio.agora = relogio.agora.plus(Duration.ofMinutes(11));
        BriefingSentinela servido = s.briefing();
        assertThat(servido.geradoEm()).isEqualTo(bom.geradoEm());
        assertThat(servido.resumo()).isEqualTo(bom.resumo());
        assertThat(servido.avisos()).anyMatch(a -> a.startsWith("Não foi possível atualizar"));
        s.briefing();
        verify(consultas, times(2)).itensFila(); // em espera: sem nova tentativa

        relogio.agora = relogio.agora.plus(Duration.ofSeconds(61));
        s.briefing();
        verify(consultas, times(3)).itensFila();
    }

    @Test
    void rotinaAgendadaRecalculaESubstituiOCache() {
        SentinelaTerritorialService s = service(semIa());
        BriefingSentinela agendado = s.atualizarAgendado();
        assertThat(agendado.origem()).isEqualTo("AGENDADA");
        assertThat(s.briefing()).isSameAs(agendado);
        verify(consultas, never()).pontosInpe(eq(5103403), anyList());
        verify(consultas, times(1)).pontosInpe(anyInt(), anyList());
    }

    @Test
    void limparRemoveUrlsTagsEMarkdown() {
        assertThat(SentinelaTerritorialService.limpar("<b>Oi</b> [link](https://a.test/x) # **x** www.b.test/y"))
                .isEqualTo("Oi link( x");
    }
}
