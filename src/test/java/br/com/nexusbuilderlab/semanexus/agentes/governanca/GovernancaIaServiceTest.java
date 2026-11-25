package br.com.nexusbuilderlab.semanexus.agentes.governanca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class GovernancaIaServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditService auditoria = mock(AuditService.class);
    private final GovernancaIaService service = new GovernancaIaService(jdbc, auditoria,
            Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC));

    private static Map<String, String> m(String... kv) {
        return GovernancaIaService.campos(kv);
    }

    // ------------------------------------------------------------- classificação da decisão

    @Test
    void mesmosCamposEAceitaIntegral() {
        var c = ComparadorDecisao.comparar(m("decisao", "CLASSIFICAR", "categoria", "PESCA_IRREGULAR",
                "prioridade", "ALTA"), m("decisao", "CLASSIFICAR", "categoria", "PESCA_IRREGULAR",
                "prioridade", "ALTA"), "decisao");
        assertThat(c.resultado()).isEqualTo(ResultadoAvaliacao.ACEITA_INTEGRAL);
        assertThat(c.camposDivergentes()).isEmpty();
    }

    @Test
    void parteDosCamposAjustadaEAjustadaParcialComOsCamposDivergentes() {
        var c = ComparadorDecisao.comparar(m("decisao", "CLASSIFICAR", "categoria", "PESCA_IRREGULAR",
                "prioridade", "ALTA"), m("decisao", "CLASSIFICAR", "categoria", "PESCA_IRREGULAR",
                "prioridade", "URGENTE"), "decisao");
        assertThat(c.resultado()).isEqualTo(ResultadoAvaliacao.AJUSTADA_PARCIAL);
        assertThat(c.camposDivergentes()).containsExactly("prioridade");
    }

    @Test
    void decisaoDiferenteEDescartada() {
        var c = ComparadorDecisao.comparar(m("decisao", "CLASSIFICAR", "categoria", "PESCA_IRREGULAR",
                "prioridade", "ALTA"), m("decisao", "SOLICITAR_COMPLEMENTACAO"), "decisao");
        assertThat(c.resultado()).isEqualTo(ResultadoAvaliacao.DESCARTADA);
        assertThat(c.camposDivergentes()).containsExactly("decisao", "categoria", "prioridade");
    }

    @Test
    void todosOsCamposDiferentesNaDistribuicaoEDescartada() {
        var c = ComparadorDecisao.comparar(m("equipeId", "1", "fiscalId", "2", "prazo", "2026-12-01"),
                m("equipeId", "3", "fiscalId", "4", "prazo", "2026-12-05"), null);
        assertThat(c.resultado()).isEqualTo(ResultadoAvaliacao.DESCARTADA);
        var parcial = ComparadorDecisao.comparar(m("equipeId", "1", "fiscalId", "2", "prazo", "2026-12-01"),
                m("equipeId", "1", "fiscalId", "2", "prazo", "2026-12-05"), null);
        assertThat(parcial.resultado()).isEqualTo(ResultadoAvaliacao.AJUSTADA_PARCIAL);
        assertThat(parcial.camposDivergentes()).containsExactly("prazo");
    }

    @Test
    void serializacaoIdaEVolta() {
        Map<String, String> campos = m("decisao", "CLASSIFICAR", "categoria", null, "prioridade", "ALTA");
        assertThat(ComparadorDecisao.desserializar(ComparadorDecisao.serializar(campos))).isEqualTo(campos);
    }

    // ------------------------------------------------------------- registro

    @Test
    void decisaoAtualizaAUltimaSugestaoPendente() {
        Map<String, Object> linha = new LinkedHashMap<>();
        linha.put("ID", new BigDecimal(42));
        linha.put("SUGERIDO", "decisao=CLASSIFICAR;categoria=PESCA_IRREGULAR;prioridade=ALTA");
        when(jdbc.queryForList(anyString(), eq("triagem"), eq(5L), any())).thenReturn(List.of(linha));

        var c = service.registrarDecisao("triagem", 5L, 10L, "PESCA_IRREGULAR",
                m("decisao", "CLASSIFICAR", "categoria", "PESCA_IRREGULAR", "prioridade", "MEDIA"), "decisao");

        assertThat(c.resultado()).isEqualTo(ResultadoAvaliacao.AJUSTADA_PARCIAL);
        verify(jdbc).update(anyString(), eq("AJUSTADA_PARCIAL"), eq("decisao=CLASSIFICAR;categoria=PESCA_IRREGULAR;"
                + "prioridade=MEDIA"), eq("prioridade"), eq("PESCA_IRREGULAR"), eq(10L), any(), eq(42L));
    }

    @Test
    void semSugestaoRecenteNadaERegistrado() {
        when(jdbc.queryForList(anyString(), eq("triagem"), eq(5L), any())).thenReturn(List.of());
        assertThat(service.registrarDecisao("triagem", 5L, 10L, null, m("decisao", "CLASSIFICAR"), "decisao"))
                .isNull();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void falhaNoBancoNaoPropagaNaDecisaoNemNaSugestao() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("fora"));
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DataAccessResourceFailureException("fora"));
        assertThat(service.registrarDecisao("triagem", 5L, 10L, null, m("decisao", "CLASSIFICAR"), "decisao"))
                .isNull();
        service.registrarSugestao("triagem", 5L, 10L, "DEMONSTRATIVO", m("decisao", "CLASSIFICAR"));
        assertThat(service.registrarResposta(10L, "LOCAL")).isNull();
    }

    @Test
    void respostaRecebeUmIdentificadorDeExecucao() {
        String id = service.registrarResposta(10L, "DEMONSTRATIVO");
        assertThat(id).matches("[0-9a-f-]{36}");
        verify(jdbc).update(anyString(), eq(id), eq(10L), eq("DEMONSTRATIVO"), any());
    }

    @Test
    void sugestaoUsaTransacaoPropriaRequiresNewECommitaDeleteInsert() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        TransactionStatus tx = new SimpleTransactionStatus();
        when(tm.getTransaction(any())).thenReturn(tx);
        GovernancaIaService comTransacao = new GovernancaIaService(jdbc, auditoria,
                Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC), tm);

        comTransacao.registrarSugestao("triagem", 5L, 10L, "LOCAL", m("decisao", "CLASSIFICAR"));

        verify(tm).getTransaction(argThat(def ->
                def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(jdbc).update(startsWith("DELETE FROM AVALIACAO_AGENTE"), eq("triagem"), eq(5L));
        verify(jdbc).update(startsWith("INSERT INTO AVALIACAO_AGENTE"), eq("triagem"), eq(5L), eq(10L), eq("LOCAL"),
                eq("decisao=CLASSIFICAR"), any());
        verify(tm).commit(tx);
        verify(tm, never()).rollback(any());
    }

    @Test
    void sugestaoDesfazDeleteSeInsertFalharNaTransacaoPropria() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        TransactionStatus tx = new SimpleTransactionStatus();
        when(tm.getTransaction(any())).thenReturn(tx);
        when(jdbc.update(startsWith("DELETE FROM AVALIACAO_AGENTE"), any(Object[].class))).thenReturn(1);
        when(jdbc.update(startsWith("INSERT INTO AVALIACAO_AGENTE"), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("fora"));
        GovernancaIaService comTransacao = new GovernancaIaService(jdbc, auditoria,
                Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC), tm);

        comTransacao.registrarSugestao("triagem", 5L, 10L, "LOCAL", m("decisao", "CLASSIFICAR"));

        verify(tm).getTransaction(argThat(def ->
                def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(tm).rollback(tx);
        verify(tm, never()).commit(any());
    }

    // ------------------------------------------------------------- feedback do DocIA

    private static UsuarioAutenticado usuario(long id) {
        return new UsuarioAutenticado(id, "Usuário " + id, "u" + id + "@exemplo.test", Perfil.CONSULTA);
    }

    @Test
    void feedbackDoProprioAutorEGravadoEAuditado() {
        String id = "3f1c2b7e-8a3d-4a51-9d0e-2b6f9c1e7a10";
        when(jdbc.update(anyString(), eq("UTIL"), eq(7L), any(), eq(id), eq(7L))).thenReturn(1);
        service.avaliarResposta(usuario(7L), id, AvaliacaoRespostaRequest.Avaliacao.UTIL);
        verify(auditoria).registrar(eq("DOCIA_AVALIAR_RESPOSTA"), eq("AVALIACAO_AGENTE"), eq(id), anyString());
    }

    @Test
    void feedbackDeRespostaAlheiaOuInexistenteE404() {
        String id = "3f1c2b7e-8a3d-4a51-9d0e-2b6f9c1e7a10";
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        assertThatThrownBy(() -> service.avaliarResposta(usuario(8L), id, AvaliacaoRespostaRequest.Avaliacao.UTIL))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.avaliarResposta(usuario(8L), "nao-e-uuid' OR 1=1",
                AvaliacaoRespostaRequest.Avaliacao.IMPRECISA)).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(auditoria, never()).registrar(anyString(), anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------- agregação

    private static Map<String, Object> l(String agente, String categoria, String resultado, long total) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("AGENTE", agente);
        m.put("CATEGORIA", categoria);
        m.put("RESULTADO", resultado);
        m.put("TOTAL", BigDecimal.valueOf(total));
        return m;
    }

    @Test
    void agregaConcordanciaPorAgenteECategoriaEFeedback() {
        GovernancaIa g = GovernancaIaService.agregar(30, List.of(
                l("triagem", "QUEIMADA_INCENDIO", "ACEITA_INTEGRAL", 5),
                l("triagem", "QUEIMADA_INCENDIO", "AJUSTADA_PARCIAL", 2),
                l("triagem", "PESCA_IRREGULAR", "DESCARTADA", 1),
                l("distribuicao", null, "ACEITA_INTEGRAL", 1),
                l("docia", null, "UTIL", 3),
                l("docia", null, "IMPRECISA", 1)));

        assertThat(g.periodoDias()).isEqualTo(30);
        assertThat(g.porAgente()).extracting(GovernancaIa.Concordancia::agente)
                .containsExactly("distribuicao", "triagem");
        GovernancaIa.Concordancia t = g.porAgente().get(1);
        assertThat(t.avaliadas()).isEqualTo(8);
        assertThat(t.concordanciaPct()).isEqualTo(62.5);
        assertThat(t.aproveitamentoPct()).isEqualTo(87.5);
        assertThat(g.porCategoria()).extracting(GovernancaIa.Concordancia::categoria)
                .containsExactly(GovernancaIaService.SEM_CATEGORIA, "PESCA_IRREGULAR", "QUEIMADA_INCENDIO");
        assertThat(g.docia().util()).isEqualTo(3);
        assertThat(g.docia().utilPct()).isEqualTo(75.0);
    }

    @Test
    void semDadosAsTaxasSaoNulas() {
        GovernancaIa g = GovernancaIaService.agregar(30, List.of());
        assertThat(g.porAgente()).isEmpty();
        assertThat(g.docia().utilPct()).isNull();
    }
}
