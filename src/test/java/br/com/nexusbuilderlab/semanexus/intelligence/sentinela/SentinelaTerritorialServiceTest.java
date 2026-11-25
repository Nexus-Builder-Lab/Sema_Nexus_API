package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private SentinelaTerritorialService service(Roteiro modelo) {
        when(consultas.ultimaDataInpe(TipoReferencia.FOCO_CALOR)).thenReturn(Optional.of(FIM_FOCOS));
        when(consultas.ultimaDataInpe(TipoReferencia.DESMATAMENTO)).thenReturn(Optional.empty());
        when(consultas.inpePorMunicipio(anyList())).thenReturn(List.of(new ContagemInpe(5107909, "Sinop", 25, 0),
                new ContagemInpe(5103403, "Cuiabá", 12, 0)));
        when(consultas.abertasPorMunicipio()).thenReturn(Map.of(5103403, 3L));
        when(consultas.pontosInpe(eq(5107909), anyList())).thenReturn(List.of(new double[] {-11.85, -55.5}));
        LocalDateTime antiga = LocalDateTime.of(2026, 11, 1, 9, 0);
        when(consultas.itensFila()).thenReturn(List.of(
                new ItemFila(1, "DEM-2026/0001", StatusDemanda.EM_TRIAGEM, antiga),
                new ItemFila(2, "DEM-2026/0002", StatusDemanda.EM_TRIAGEM, antiga),
                new ItemFila(3, "DEM-2026/0003", StatusDemanda.EM_TRIAGEM, antiga)));
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
                relogio);
    }

    private ClienteModelo.RespostaModelo ferramentas() throws Exception {
        return new ClienteModelo.RespostaModelo("tool_calls", List.of(
                new ClienteModelo.ChamadaFerramenta("c1", "anomalias_sentinela", json.readTree("{\"tipo\":\"TODAS\"}")),
                new ClienteModelo.ChamadaFerramenta("c2", "indicadores_operacionais",
                        json.readTree("{\"escopo\":\"ESTADO\"}"))), "", null, 10, 5, 0);
    }

    private static ClienteModelo.RespostaModelo texto(String resumo) {
        return new ClienteModelo.RespostaModelo("stop", List.of(), "{\"resumo\":\"" + resumo + "\"}", null, 50, 30, 0);
    }

    @Test
    void semIaUsaTemplateIdentificadoComAsTresRegras() {
        Roteiro modelo = new Roteiro();
        modelo.disponivel = false;
        BriefingSentinela b = service(modelo).briefing();

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
        verify(auditoria).registrar(eq("AGENTE_DEMONSTRATIVO"), eq("AGENTE"), eq("sentinela:briefing-2026-11-24"),
                anyString());
    }

    @Test
    void janelaDoInpeTerminaNoRegistroMaisRecenteDoPacote() {
        Roteiro modelo = new Roteiro();
        modelo.disponivel = false;
        BriefingSentinela b = service(modelo).briefing();

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
        assertThat(sinop.acoes().get(1).parametros()).containsEntry("origem", "MONITORAMENTO")
                .containsEntry("latitude", -11.85);
    }

    @Test
    void resumoDaIaComNumerosConferidosEUsado() throws Exception {
        Roteiro modelo = new Roteiro();
        modelo.respostas.add(ferramentas());
        modelo.respostas.add(texto("Há 3 anomalias, 1 de severidade alta: a Equipe A tem 6 demandas ativas e 2 "
                + "atrasadas. Sinop teve 25 focos sem demanda aberta; sugere-se abrir demanda de monitoramento."));
        BriefingSentinela b = service(modelo).briefing();

        assertThat(b.modo()).isEqualTo("LOCAL");
        assertThat(b.modelo()).isEqualTo("qwen3:4b");
        assertThat(b.resumo()).startsWith("Há 3 anomalias");
        assertThat(b.avisos()).isEmpty();
        assertThat(b.evidencias()).anyMatch(e -> e.startsWith("IA: Consultou 3 anomalia"));
        verify(auditoria).registrar(eq("AGENTE_EXECUCAO"), eq("AGENTE"), anyString(), anyString());
    }

    @Test
    void numeroInventadoPelaIaCaiNoTemplate() throws Exception {
        Roteiro modelo = new Roteiro();
        modelo.respostas.add(ferramentas());
        modelo.respostas.add(texto("Há 3 anomalias e 48 municípios em alerta crítico; Sinop teve 25 focos de calor "
                + "na janela recente."));
        BriefingSentinela b = service(modelo).briefing();

        assertThat(b.modo()).isEqualTo("DEMONSTRATIVO");
        assertThat(b.resumo()).startsWith("Briefing de 24/11/2026");
        assertThat(b.avisos()).singleElement().asString().contains("número ausente");
    }

    @Test
    void falhaDaIaCaiNoTemplateComAviso() {
        Roteiro modelo = new Roteiro(); // disponível, mas sem respostas: o executor falha
        BriefingSentinela b = service(modelo).briefing();
        assertThat(b.modo()).isEqualTo("DEMONSTRATIVO");
        assertThat(b.avisos()).isNotEmpty();
    }

    @Test
    void cacheServeOMesmoBriefingAteVencer() {
        Roteiro modelo = new Roteiro();
        modelo.disponivel = false;
        SentinelaTerritorialService s = service(modelo);
        BriefingSentinela primeiro = s.briefing();
        relogio.agora = relogio.agora.plus(Duration.ofMinutes(9));
        assertThat(s.briefing()).isSameAs(primeiro);
        verify(consultas, times(1)).itensFila();

        relogio.agora = relogio.agora.plus(Duration.ofMinutes(2));
        assertThat(s.briefing()).isNotSameAs(primeiro);
        verify(consultas, times(2)).itensFila();
    }

    @Test
    void rotinaAgendadaRecalculaESubstituiOCache() {
        Roteiro modelo = new Roteiro();
        modelo.disponivel = false;
        SentinelaTerritorialService s = service(modelo);
        BriefingSentinela agendado = s.atualizarAgendado();
        assertThat(agendado.origem()).isEqualTo("AGENDADA");
        assertThat(s.briefing()).isSameAs(agendado);
        verify(consultas, never()).pontosInpe(eq(5103403), anyList());
        verify(consultas, times(1)).pontosInpe(anyInt(), anyList());
    }
}
