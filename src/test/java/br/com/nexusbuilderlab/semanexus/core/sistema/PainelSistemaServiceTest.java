package br.com.nexusbuilderlab.semanexus.core.sistema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import br.com.nexusbuilderlab.semanexus.agentes.AgentesConfiguracao;
import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo;
import br.com.nexusbuilderlab.semanexus.agentes.ExecutorAgente;
import br.com.nexusbuilderlab.semanexus.agentes.MetricasAgentes;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.sistema.PainelSistema.Estado;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class PainelSistemaServiceTest {

    @TempDir
    Path uploads;

    private final AgentesConfiguracao config = new AgentesConfiguracao("ollama", "", "claude-haiku-5-5",
            "claude-sonnet-5-5", "http://ollama:11434", "qwen3:4b-instruct", false, 30, 6);

    /** Provedor simulado com diagnóstico (sem rede). */
    static class Provedor implements ClienteModelo {
        boolean disponivel = true;

        @Override
        public boolean disponivel() {
            return disponivel;
        }

        @Override
        public String modelo(String sugerido) {
            return "qwen3:4b-instruct";
        }

        @Override
        public Map<String, Object> diagnostico() {
            return Map.of("provedor", "Ollama (local)", "versao", "0.40.11");
        }

        @Override
        public RespostaModelo criar(PedidoModelo pedido) {
            throw new IllegalStateException("não usado");
        }
    }

    private PainelSistemaService servico(Provedor provedor, JdbcTemplate jdbc, ReferenciaSateliteService ref,
                                         MetricasAgentes metricas, String dir) {
        ExecutorAgente executor = new ExecutorAgente(provedor, config, mock(AuditService.class), new ObjectMapper(),
                metricas);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("demo");
        return new PainelSistemaService(jdbc, executor, config, metricas, ref, env, dir);
    }

    @SuppressWarnings("unchecked")
    private static JdbcTemplate bancoOk() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT 1 FROM DUAL"), eq(Integer.class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(120L);
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("Oracle Database 23ai Free");
        return jdbc;
    }

    private static ReferenciaSateliteService inpe() {
        ReferenciaSateliteService ref = mock(ReferenciaSateliteService.class);
        when(ref.metadados()).thenReturn(new ReferenciaDto.Metadados("INPE — dados públicos", false,
                LocalDateTime.now(), "aviso", List.of(new ReferenciaDto.Importacao(1L, "INPE — Programa Queimadas",
                        "FOCO_CALOR", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 31), LocalDateTime.now(),
                        5000, "https://exemplo.test/focos", "CC BY-SA", "focos.csv.gz"))));
        return ref;
    }

    @Test
    void tudoNoArComMetricasDosAgentes() throws Exception {
        Files.writeString(uploads.resolve("doc.pdf"), "conteúdo");
        MetricasAgentes metricas = new MetricasAgentes();
        metricas.sucesso("triagem", "qwen3:4b-instruct", 8000);
        metricas.sucesso("triagem", "qwen3:4b-instruct", 10000);
        metricas.falha("triagem", "qwen3:4b-instruct", 30000, "JSON inválido");
        metricas.demonstrativo("docia");

        PainelSistema p = servico(new Provedor(), bancoOk(), inpe(), metricas, uploads.toString()).montar();

        assertThat(p.verificacoes()).extracting(PainelSistema.Verificacao::id)
                .containsExactly("api", "banco", "ia", "inpe", "armazenamento");
        assertThat(p.verificacoes()).allMatch(v -> v.estado() == Estado.OK);
        assertThat(p.verificacoes().get(1).detalhes()).containsEntry("demandas", 120L)
                .containsEntry("versao", "Oracle Database 23ai Free");
        assertThat(p.verificacoes().get(2).detalhes()).containsEntry("versao", "0.40.11")
                .containsEntry("modo", "LOCAL");
        assertThat(p.verificacoes().get(3).resumo()).contains("5000 registros");
        assertThat(p.verificacoes().get(4).detalhes()).containsEntry("arquivos", 1L);
        var triagem = p.agentes().get(0);
        assertThat(triagem.modelo()).isEqualTo("qwen3:4b-instruct");
        assertThat(triagem.metricas().execucoes()).isEqualTo(3);
        assertThat(triagem.metricas().sucessos()).isEqualTo(2);
        assertThat(triagem.metricas().tempoMedioMs()).isEqualTo(9000);
        assertThat(triagem.metricas().ultimaFalha()).isEqualTo("JSON inválido");
        assertThat(p.agentes().get(1).metricas().demonstrativo()).isEqualTo(1);
        assertThat(p.agentes()).anyMatch(a -> a.id().equals("minuta")
                && a.ferramentas().contains("alertas_inpe_area"));
        assertThat(p.agentes()).anyMatch(a -> a.id().equals("sentinela")
                && a.ferramentas().contains("anomalias_sentinela"));
        assertThat(p.agentes()).anyMatch(a -> a.id().equals("assistente-global")
                && a.ferramentas().contains("prazos_criticos") && a.ferramentas().contains("buscar_documentos"));
        assertThat(p.sistema().perfilAtivo()).isEqualTo("demo");
    }

    @Test
    void falhasViramIndisponivelSemDerrubarOPainel() {
        Provedor provedor = new Provedor();
        provedor.disponivel = false;
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT 1 FROM DUAL"), eq(Integer.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("sem conexão"));
        ReferenciaSateliteService ref = mock(ReferenciaSateliteService.class);
        when(ref.metadados()).thenReturn(new ReferenciaDto.Metadados("INPE", false, null, "", List.of()));

        PainelSistema p = servico(provedor, jdbc, ref, new MetricasAgentes(), uploads.resolve("nao-existe").toString())
                .montar();

        Map<String, Estado> estados = new java.util.HashMap<>();
        p.verificacoes().forEach(v -> estados.put(v.id(), v.estado()));
        assertThat(estados).containsEntry("api", Estado.OK).containsEntry("banco", Estado.INDISPONIVEL)
                .containsEntry("ia", Estado.INDISPONIVEL).containsEntry("inpe", Estado.ATENCAO)
                .containsEntry("armazenamento", Estado.INDISPONIVEL);
        assertThat(p.agentes()).allMatch(a -> a.modo().equals("DEMONSTRATIVO"));
        assertThat(p.verificacoes().get(1).resumo()).doesNotContain("sem conexão"); // só o tipo do erro
    }
}
