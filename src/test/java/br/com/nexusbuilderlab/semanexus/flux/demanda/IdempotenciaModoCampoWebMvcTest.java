package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.IdempotenciaSincronizacaoService.ResultadoIdempotente;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.HistoricoItem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** Fatia web da Issue #147: cabeçalho {@code Idempotency-Key}, replay, conflito 409, validação 400 e @PreAuthorize. */
@WebMvcTest(controllers = DemandaController.class)
@Import({SecurityConfig.class, IdempotenciaModoCampoWebMvcTest.Config.class})
class IdempotenciaModoCampoWebMvcTest {

    static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres!!";
    static final String PROBLEM_JSON = "application/problem+json";

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService(SEGREDO, Duration.ofHours(8), Clock.systemUTC());
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean DemandaService demandas;
    @MockitoBean TriagemService triagem;
    @MockitoBean DistribuicaoService distribuicao;
    @MockitoBean AcompanhamentoService acompanhamento;
    @MockitoBean FiscalizacaoService fiscalizacaoService;
    @MockitoBean ConclusaoService conclusao;
    @MockitoBean IdempotenciaSincronizacaoService idempotencia;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 200L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    private static String atividadeJson() {
        return "{\"descricao\":\"Vistoria em campo realizada\",\"data\":\"" + LocalDate.now() + "\"}";
    }

    private static String fiscalizacaoJson() {
        return "{\"data\":\"" + LocalDate.now() + "\",\"atividades\":\"Vistoria\",\"resultado\":\"Regular\","
                + "\"evidencias\":\"Fotos\"}";
    }

    private static HistoricoItem item() {
        return new HistoricoItem(33L, TipoHistorico.ATIVIDADE, StatusDemanda.EM_ANDAMENTO, StatusDemanda.EM_ANDAMENTO,
                "Vistoria em campo realizada", new UsuarioRef(202L, "Fiscal"), LocalDateTime.now());
    }

    private static DemandaDetalhe detalhe(StatusDemanda status) {
        return new DemandaDetalhe(5L, "DEM-2026/0005", "Título", "Desc", Origem.DENUNCIA, null,
                new MunicipioRef(5103403, "Cuiabá", Bioma.CERRADO), null, null, null, null, null, LocalDate.now(),
                null, null, null, status, false, null, new UsuarioRef(202L, "Fiscal"), null, null, null, null,
                null, 0, new UsuarioRef(201L, "Gestor"), LocalDateTime.now(), null);
    }

    @Test
    void exigeAutenticacaoEPreAuthorizeNosEndpointsDeSincronizacao() throws Exception {
        mvc.perform(post("/api/demandas/5/inicio").header(ChaveIdempotencia.CABECALHO, "k-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        mvc.perform(post("/api/demandas/5/atividades").header(ChaveIdempotencia.CABECALHO, "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(atividadeJson()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/demandas/5/fiscalizacao").header(ChaveIdempotencia.CABECALHO, "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(fiscalizacaoJson()))
                .andExpect(status().isUnauthorized());

        for (Perfil negado : List.of(Perfil.ANALISTA, Perfil.CONSULTA)) {
            mvc.perform(post("/api/demandas/5/inicio")
                            .header(HttpHeaders.AUTHORIZATION, bearer(negado))
                            .header(ChaveIdempotencia.CABECALHO, "k-1"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
            mvc.perform(post("/api/demandas/5/atividades")
                            .header(HttpHeaders.AUTHORIZATION, bearer(negado))
                            .header(ChaveIdempotencia.CABECALHO, "k-1")
                            .contentType(MediaType.APPLICATION_JSON).content(atividadeJson()))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
            mvc.perform(post("/api/demandas/5/fiscalizacao")
                            .header(HttpHeaders.AUTHORIZATION, bearer(negado))
                            .header(ChaveIdempotencia.CABECALHO, "k-1")
                            .contentType(MediaType.APPLICATION_JSON).content(fiscalizacaoJson()))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        }
        verify(idempotencia, never()).iniciar(any(), any(), any());
        verify(idempotencia, never()).registrarAtividade(any(), any(), any(), any());
        verify(idempotencia, never()).registrarFiscalizacao(any(), any(), any(), any());
    }

    @Test
    void atividadeComIdempotencyKeyDevolve201EReplayMarcaCabecalho() throws Exception {
        when(idempotencia.registrarAtividade(any(), eq(5L), any(), eq("uuid-ativ-1")))
                .thenReturn(new ResultadoIdempotente<>(201, item(), false))
                .thenReturn(new ResultadoIdempotente<>(201, item(), true));

        String token = bearer(Perfil.FISCAL);
        mvc.perform(post("/api/demandas/5/atividades")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(ChaveIdempotencia.CABECALHO, "uuid-ativ-1")
                        .contentType(MediaType.APPLICATION_JSON).content(atividadeJson()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(ChaveIdempotencia.CABECALHO_REPLAY))
                .andExpect(jsonPath("$.id").value(33));

        mvc.perform(post("/api/demandas/5/atividades")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(ChaveIdempotencia.CABECALHO, "uuid-ativ-1")
                        .contentType(MediaType.APPLICATION_JSON).content(atividadeJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string(ChaveIdempotencia.CABECALHO_REPLAY, "true"))
                .andExpect(jsonPath("$.id").value(33));
    }

    @Test
    void fiscalizacaoEInicioComIdempotencyKeyDevolvem200EReplayMarcaCabecalho() throws Exception {
        when(idempotencia.iniciar(any(), eq(5L), eq("uuid-ini-1")))
                .thenReturn(new ResultadoIdempotente<>(200, detalhe(StatusDemanda.EM_ANDAMENTO), false))
                .thenReturn(new ResultadoIdempotente<>(200, detalhe(StatusDemanda.EM_ANDAMENTO), true));
        when(idempotencia.registrarFiscalizacao(any(), eq(5L), any(), eq("uuid-fisc-1")))
                .thenReturn(new ResultadoIdempotente<>(200, detalhe(StatusDemanda.AGUARDANDO_CONCLUSAO), false))
                .thenReturn(new ResultadoIdempotente<>(200, detalhe(StatusDemanda.AGUARDANDO_CONCLUSAO), true));

        String token = bearer(Perfil.GESTOR);
        mvc.perform(post("/api/demandas/5/inicio")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(ChaveIdempotencia.CABECALHO, "uuid-ini-1"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(ChaveIdempotencia.CABECALHO_REPLAY))
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
        mvc.perform(post("/api/demandas/5/inicio")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(ChaveIdempotencia.CABECALHO, "uuid-ini-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(ChaveIdempotencia.CABECALHO_REPLAY, "true"))
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));

        mvc.perform(post("/api/demandas/5/fiscalizacao")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(ChaveIdempotencia.CABECALHO, "uuid-fisc-1")
                        .contentType(MediaType.APPLICATION_JSON).content(fiscalizacaoJson()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(ChaveIdempotencia.CABECALHO_REPLAY))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_CONCLUSAO"));
        mvc.perform(post("/api/demandas/5/fiscalizacao")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(ChaveIdempotencia.CABECALHO, "uuid-fisc-1")
                        .contentType(MediaType.APPLICATION_JSON).content(fiscalizacaoJson()))
                .andExpect(status().isOk())
                .andExpect(header().string(ChaveIdempotencia.CABECALHO_REPLAY, "true"))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_CONCLUSAO"));
    }

    @Test
    void chaveInvalidaRetorna400ProblemJsonSemChamarServico() throws Exception {
        String token = bearer(Perfil.FISCAL);
        for (String invalida : List.of("", "   ", "a".repeat(129), "chave invalida", "chave/1")) {
            mvc.perform(post("/api/demandas/5/atividades")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(ChaveIdempotencia.CABECALHO, invalida)
                            .contentType(MediaType.APPLICATION_JSON).content(atividadeJson()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.errors[0].campo").value("Idempotency-Key"));
        }
        mvc.perform(post("/api/demandas/5/inicio")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(ChaveIdempotencia.CABECALHO, ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].campo").value("Idempotency-Key"));
        verify(idempotencia, never()).registrarAtividade(any(), any(), any(), any());
        verify(idempotencia, never()).iniciar(any(), any(), any());
    }

    @Test
    void conflitoDePayloadEFiscalForaDoEscopoRetornamProblemJson() throws Exception {
        String token = bearer(Perfil.FISCAL);
        when(idempotencia.registrarAtividade(any(), eq(5L), any(), eq("chave-conflito")))
                .thenThrow(new ConflitoException("A chave de idempotência já foi utilizada com outro conteúdo."));
        mvc.perform(post("/api/demandas/5/atividades")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(ChaveIdempotencia.CABECALHO, "chave-conflito")
                        .contentType(MediaType.APPLICATION_JSON).content(atividadeJson()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));

        when(idempotencia.registrarFiscalizacao(any(), eq(9L), any(), eq("chave-403")))
                .thenThrow(new AccessDeniedException("Somente o fiscal designado para a demanda pode executar esta ação."));
        mvc.perform(post("/api/demandas/9/fiscalizacao")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(ChaveIdempotencia.CABECALHO, "chave-403")
                        .contentType(MediaType.APPLICATION_JSON).content(fiscalizacaoJson()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void semCabecalhoIdempotencyKeySegueFluxoNormal() throws Exception {
        when(acompanhamento.registrarAtividade(any(), eq(5L), any())).thenReturn(item());
        mvc.perform(post("/api/demandas/5/atividades")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Perfil.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(atividadeJson()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(ChaveIdempotencia.CABECALHO_REPLAY));
        verify(acompanhamento).registrarAtividade(any(), eq(5L), any());
        verify(idempotencia, never()).registrarAtividade(any(), any(), any(), any());
    }
}
