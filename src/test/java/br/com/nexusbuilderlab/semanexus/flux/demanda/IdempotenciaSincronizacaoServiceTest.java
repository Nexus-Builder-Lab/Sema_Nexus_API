package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.flux.demanda.IdempotenciaSincronizacaoService.ResultadoIdempotente;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.AtividadeRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FiscalizacaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.HistoricoItem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotenciaSincronizacaoServiceTest {

    static final Instant AGORA = Instant.parse("2026-11-25T15:00:00Z");
    static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);
    static final LocalDate HOJE = LocalDate.of(2026, 11, 25);

    @Mock DemandaRepository demandas;
    @Mock ChaveIdempotenciaRepository chaves;
    @Mock AcompanhamentoService acompanhamento;
    @Mock FiscalizacaoService fiscalizacaoService;

    IdempotenciaSincronizacaoService service;
    Map<String, ChaveIdempotencia> armazenamento;
    Usuario fiscal;
    Usuario outroFiscal;
    Usuario gestor;
    Demanda demanda5;
    Demanda demanda6;

    @BeforeEach
    void preparar() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        service = new IdempotenciaSincronizacaoService(
                demandas, chaves, acompanhamento, fiscalizacaoService, mapper, RELOGIO, Duration.ofHours(24));
        armazenamento = new ConcurrentHashMap<>();

        when(chaves.buscarParaEscrita(any(), any(), any(), any())).thenAnswer(inv -> {
            String key = escopo(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3));
            return Optional.ofNullable(armazenamento.get(key));
        });
        when(chaves.save(any())).thenAnswer(inv -> {
            ChaveIdempotencia c = inv.getArgument(0);
            String key = escopo(c.getUsuarioId(), c.getOperacao(), c.getDemandaId(), c.getChave());
            armazenamento.put(key, c);
            return c;
        });
        org.mockito.Mockito.doAnswer(inv -> {
            ChaveIdempotencia c = inv.getArgument(0);
            armazenamento.remove(escopo(c.getUsuarioId(), c.getOperacao(), c.getDemandaId(), c.getChave()));
            return null;
        }).when(chaves).delete(any());

        fiscal = usuario(20L, Perfil.FISCAL);
        outroFiscal = usuario(21L, Perfil.FISCAL);
        gestor = usuario(10L, Perfil.GESTOR);
        demanda5 = demanda(5L, fiscal);
        demanda6 = demanda(6L, fiscal);
        when(demandas.buscarParaEscrita(5L)).thenReturn(Optional.of(demanda5));
        when(demandas.buscarParaEscrita(6L)).thenReturn(Optional.of(demanda6));
    }

    private static String escopo(Long usuarioId, OperacaoIdempotente op, Long demandaId, String chave) {
        return usuarioId + "|" + op + "|" + demandaId + "|" + chave;
    }

    private static Usuario usuario(long id, Perfil perfil) {
        Usuario u = new Usuario("Usuário " + id, "u" + id + "@exemplo.test", "hash", perfil);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Demanda demanda(long id, Usuario fiscal) {
        Demanda d = new Demanda("DEM-2026/000" + id, fiscal);
        ReflectionTestUtils.setField(d, "id", id);
        d.setStatus(StatusDemanda.EM_ANDAMENTO);
        d.setFiscal(fiscal);
        return d;
    }

    private static HistoricoItem historico(long id, String texto) {
        return new HistoricoItem(id, TipoHistorico.ATIVIDADE, StatusDemanda.EM_ANDAMENTO,
                StatusDemanda.EM_ANDAMENTO, texto, new UsuarioRef(20L, "Fiscal 20"),
                LocalDateTime.of(2026, 11, 25, 11, 0));
    }

    private static DemandaDetalhe detalhe(long id, StatusDemanda status) {
        return new DemandaDetalhe(id, "DEM-2026/000" + id, "Título", "Desc", Origem.DENUNCIA, null,
                new MunicipioRef(5103403, "Cuiabá", Bioma.CERRADO), null, null, null, null, null, HOJE,
                null, null, null, status, false, null, new UsuarioRef(20L, "Fiscal 20"), null, null, null,
                null, null, 0, new UsuarioRef(10L, "Gestor"), LocalDateTime.of(2026, 11, 20, 9, 0), null);
    }

    @Test
    void reenvioDeAtividadeComMesmaChaveNaoDuplicaEDevolveMesmaResposta() {
        AtividadeRequest req = new AtividadeRequest("Vistoria em área rural", HOJE);
        HistoricoItem esperado = historico(91L, "[2026-11-25] Vistoria em área rural");
        when(acompanhamento.registrarAtividade(any(), eq(5L), eq(req))).thenReturn(esperado);

        ResultadoIdempotente<HistoricoItem> primeira =
                service.registrarAtividade(UsuarioAutenticado.de(fiscal), 5L, req, "chave-ativ-1");
        ResultadoIdempotente<HistoricoItem> segunda =
                service.registrarAtividade(UsuarioAutenticado.de(fiscal), 5L, req, "chave-ativ-1");

        assertThat(primeira.statusHttp()).isEqualTo(201);
        assertThat(primeira.repetida()).isFalse();
        assertThat(primeira.corpo()).isEqualTo(esperado);

        assertThat(segunda.statusHttp()).isEqualTo(201);
        assertThat(segunda.repetida()).isTrue();
        assertThat(segunda.corpo()).isEqualTo(esperado);
        verify(acompanhamento, times(1)).registrarAtividade(any(), eq(5L), eq(req));
    }

    @Test
    void chavesDistintasCriamRegistrosSeparados() {
        AtividadeRequest req1 = new AtividadeRequest("Atividade 1", HOJE);
        AtividadeRequest req2 = new AtividadeRequest("Atividade 2", HOJE);
        when(acompanhamento.registrarAtividade(any(), eq(5L), eq(req1))).thenReturn(historico(1L, "A1"));
        when(acompanhamento.registrarAtividade(any(), eq(5L), eq(req2))).thenReturn(historico(2L, "A2"));

        ResultadoIdempotente<HistoricoItem> r1 =
                service.registrarAtividade(UsuarioAutenticado.de(fiscal), 5L, req1, "chave-1");
        ResultadoIdempotente<HistoricoItem> r2 =
                service.registrarAtividade(UsuarioAutenticado.de(fiscal), 5L, req2, "chave-2");

        assertThat(r1.repetida()).isFalse();
        assertThat(r2.repetida()).isFalse();
        assertThat(r1.corpo().id()).isEqualTo(1L);
        assertThat(r2.corpo().id()).isEqualTo(2L);
        verify(acompanhamento, times(2)).registrarAtividade(any(), eq(5L), any());
    }

    @Test
    void reenvioDeFiscalizacaoEInicioDevolvemRespostaConsistenteSemDuplicar() {
        FiscalizacaoRequest fisc = new FiscalizacaoRequest(HOJE, "Vistoria", "Irregularidade", "Fotos", null);
        DemandaDetalhe aguardo = detalhe(5L, StatusDemanda.AGUARDANDO_CONCLUSAO);
        when(fiscalizacaoService.registrar(any(), eq(5L), eq(fisc))).thenReturn(aguardo);

        ResultadoIdempotente<DemandaDetalhe> f1 =
                service.registrarFiscalizacao(UsuarioAutenticado.de(fiscal), 5L, fisc, "chave-fisc-1");
        ResultadoIdempotente<DemandaDetalhe> f2 =
                service.registrarFiscalizacao(UsuarioAutenticado.de(fiscal), 5L, fisc, "chave-fisc-1");

        assertThat(f1.statusHttp()).isEqualTo(200);
        assertThat(f1.repetida()).isFalse();
        assertThat(f2.statusHttp()).isEqualTo(200);
        assertThat(f2.repetida()).isTrue();
        assertThat(f2.corpo()).isEqualTo(aguardo);
        verify(fiscalizacaoService, times(1)).registrar(any(), eq(5L), eq(fisc));

        DemandaDetalhe emAndamento = detalhe(6L, StatusDemanda.EM_ANDAMENTO);
        when(acompanhamento.iniciar(any(), eq(6L))).thenReturn(emAndamento);
        ResultadoIdempotente<DemandaDetalhe> i1 = service.iniciar(UsuarioAutenticado.de(fiscal), 6L, "chave-ini-1");
        ResultadoIdempotente<DemandaDetalhe> i2 = service.iniciar(UsuarioAutenticado.de(fiscal), 6L, "chave-ini-1");

        assertThat(i1.repetida()).isFalse();
        assertThat(i2.repetida()).isTrue();
        assertThat(i2.corpo()).isEqualTo(emAndamento);
        verify(acompanhamento, times(1)).iniciar(any(), eq(6L));
    }

    @Test
    void mesmaChaveComPayloadDiferenteResponde409SemExecutar() {
        AtividadeRequest original = new AtividadeRequest("Descrição A", HOJE);
        AtividadeRequest alterado = new AtividadeRequest("Descrição B", HOJE);
        when(acompanhamento.registrarAtividade(any(), eq(5L), eq(original))).thenReturn(historico(10L, "A"));

        service.registrarAtividade(UsuarioAutenticado.de(fiscal), 5L, original, "chave-conflito");

        assertThatThrownBy(() -> service.registrarAtividade(
                UsuarioAutenticado.de(fiscal), 5L, alterado, "chave-conflito"))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("outro conteúdo");
        verify(acompanhamento, times(1)).registrarAtividade(any(), eq(5L), any());
    }

    @Test
    void checklistComOrdemDeChavesDiferenteConsideradoMesmoPayload() {
        Map<String, Object> checklist1 = new LinkedHashMap<>();
        checklist1.put("local_acessivel", true);
        checklist1.put("area_atingida_ha", 4.5);
        Map<String, Object> checklist2 = new LinkedHashMap<>();
        checklist2.put("area_atingida_ha", 4.5);
        checklist2.put("local_acessivel", true);

        FiscalizacaoRequest req1 = new FiscalizacaoRequest(HOJE, "Ativ", "Res", "Evid", null, checklist1);
        FiscalizacaoRequest req2 = new FiscalizacaoRequest(HOJE, "Ativ", "Res", "Evid", null, checklist2);
        DemandaDetalhe esperado = detalhe(5L, StatusDemanda.AGUARDANDO_CONCLUSAO);
        when(fiscalizacaoService.registrar(any(), eq(5L), eq(req1))).thenReturn(esperado);

        service.registrarFiscalizacao(UsuarioAutenticado.de(fiscal), 5L, req1, "chave-json-canonico");
        ResultadoIdempotente<DemandaDetalhe> replay =
                service.registrarFiscalizacao(UsuarioAutenticado.de(fiscal), 5L, req2, "chave-json-canonico");

        assertThat(replay.repetida()).isTrue();
        assertThat(replay.corpo()).isEqualTo(esperado);
        verify(fiscalizacaoService, times(1)).registrar(any(), eq(5L), any());
    }

    @Test
    void escopoIsolaPorUsuarioEDemandaEOperacao() {
        AtividadeRequest req = new AtividadeRequest("Mesma atividade", HOJE);
        when(acompanhamento.registrarAtividade(any(), eq(5L), eq(req))).thenReturn(historico(1L, "D5"));
        when(acompanhamento.registrarAtividade(any(), eq(6L), eq(req))).thenReturn(historico(2L, "D6"));
        when(fiscalizacaoService.registrar(any(), eq(5L), any()))
                .thenReturn(detalhe(5L, StatusDemanda.AGUARDANDO_CONCLUSAO));

        // Mesmo usuário em demandas distintas com mesma chave
        ResultadoIdempotente<HistoricoItem> d5 =
                service.registrarAtividade(UsuarioAutenticado.de(fiscal), 5L, req, "chave-compartilhada");
        ResultadoIdempotente<HistoricoItem> d6 =
                service.registrarAtividade(UsuarioAutenticado.de(fiscal), 6L, req, "chave-compartilhada");
        // Usuário distinto (GESTOR) na mesma demanda com mesma chave
        ResultadoIdempotente<HistoricoItem> g5 =
                service.registrarAtividade(UsuarioAutenticado.de(gestor), 5L, req, "chave-compartilhada");
        // Operação distinta (fiscalizacao) na mesma demanda e usuário com mesma chave
        ResultadoIdempotente<DemandaDetalhe> f5 = service.registrarFiscalizacao(
                UsuarioAutenticado.de(fiscal), 5L,
                new FiscalizacaoRequest(HOJE, "A", "R", "E", null), "chave-compartilhada");

        assertThat(d5.repetida()).isFalse();
        assertThat(d6.repetida()).isFalse();
        assertThat(g5.repetida()).isFalse();
        assertThat(f5.repetida()).isFalse();
    }

    @Test
    void fiscalNaoDesignadoRecebe403EDemandaInexistenteRecebe404() {
        AtividadeRequest req = new AtividadeRequest("Tentativa", HOJE);
        assertThatThrownBy(() -> service.registrarAtividade(
                UsuarioAutenticado.de(outroFiscal), 5L, req, "chave-403"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.registrarAtividade(
                UsuarioAutenticado.de(fiscal), 999L, req, "chave-404"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(chaves, never()).save(any());
        verify(acompanhamento, never()).registrarAtividade(any(), any(), any());
    }

    @Test
    void validacaoDeTamanhoEFormatoDaChave() {
        for (String invalida : new String[] {
            null, "", "   ", "a".repeat(129), "chave com espaco", "chave/barra", "chave@invalida", "chave\nquebra"
        }) {
            assertThatThrownBy(() -> ChaveIdempotencia.validarChave(invalida))
                    .as("chave inválida: %s", invalida)
                    .isInstanceOf(DadosInvalidosException.class)
                    .satisfies(ex -> assertThat(((DadosInvalidosException) ex).getErros())
                            .extracting("campo").containsExactly("Idempotency-Key"));
        }
        assertThat(ChaveIdempotencia.validarChave("550e8400-e29b-41d4-a716-446655440000"))
                .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(ChaveIdempotencia.validarChave("campo:demanda_5.item-1")).isEqualTo("campo:demanda_5.item-1");
        assertThat(ChaveIdempotencia.validarChave("a".repeat(128))).hasSize(128);
    }

    @Test
    void falhaNaRegraDeNegocioNaoGravaChave() {
        AtividadeRequest req = new AtividadeRequest("Inválida", HOJE);
        when(acompanhamento.registrarAtividade(any(), eq(5L), eq(req)))
                .thenThrow(new ConflitoException("Demanda não está em andamento."));

        assertThatThrownBy(() -> service.registrarAtividade(
                UsuarioAutenticado.de(fiscal), 5L, req, "chave-erro"))
                .isInstanceOf(ConflitoException.class);
        verify(chaves, never()).save(any());
    }

    @Test
    void registroExpiradoESubstituidoELimpezaRemoveExpirados() {
        AtividadeRequest req = new AtividadeRequest("Nova tentativa", HOJE);
        LocalDateTime criado = LocalDateTime.now(RELOGIO).minusHours(30);
        ChaveIdempotencia expirada = new ChaveIdempotencia(20L, 5L, OperacaoIdempotente.ATIVIDADE, "chave-exp",
                "0".repeat(64), 201, "{}", criado, criado.plusHours(24));
        armazenamento.put(escopo(20L, OperacaoIdempotente.ATIVIDADE, 5L, "chave-exp"), expirada);
        when(acompanhamento.registrarAtividade(any(), eq(5L), eq(req))).thenReturn(historico(77L, "Nova"));

        ResultadoIdempotente<HistoricoItem> res =
                service.registrarAtividade(UsuarioAutenticado.de(fiscal), 5L, req, "chave-exp");

        assertThat(res.repetida()).isFalse();
        assertThat(res.corpo().id()).isEqualTo(77L);
        verify(chaves).delete(expirada);
        verify(chaves).flush();

        when(chaves.removerExpiradas(LocalDateTime.now(RELOGIO))).thenReturn(3);
        assertThat(service.limparExpiradas()).isEqualTo(3);
    }
}
