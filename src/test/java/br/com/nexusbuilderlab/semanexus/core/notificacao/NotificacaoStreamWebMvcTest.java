package br.com.nexusbuilderlab.semanexus.core.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.notificacao.dto.ContagemNaoLidas;
import br.com.nexusbuilderlab.semanexus.core.notificacao.dto.NotificacaoDto;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Stream SSE de notificações (Issue #148) pela cadeia de segurança real: escopo por usuário, 401 sem token (e com
 * token só na query string), evento inicial, limite por usuário, 503 acima do total e encerramento no logout.
 */
@WebMvcTest(controllers = NotificacaoController.class)
@Import({SecurityConfig.class, NotificacaoStreamWebMvcTest.Config.class})
class NotificacaoStreamWebMvcTest {

    static final String STREAM = "/api/notificacoes/stream";
    static final Pattern EVENTO = Pattern.compile("(?m)^event:(\\S+)$");

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService("segredo-de-teste-com-mais-de-32-caracteres!!", Duration.ofHours(8),
                    Clock.systemUTC());
        }

        @Bean
        CanalNotificacoes canalNotificacoes() {
            return new CanalNotificacoes(Duration.ofMinutes(5), Duration.ofMillis(50), 2, 4, Clock.systemUTC());
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;
    @Autowired
    CanalNotificacoes canal;
    @MockitoBean
    UsuarioRepository usuarios;
    @MockitoBean
    AuditService auditService;
    @MockitoBean
    NotificacaoService service;

    private final List<String> tokens = new ArrayList<>();

    @AfterEach
    void fecharTudo() {
        tokens.forEach(t -> canal.encerrarDoToken(jwtService.validar(t).orElseThrow().jti()));
        tokens.clear();
    }

    private String token(Perfil perfil, long id) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + id + "@exemplo.test", "hash", perfil);
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        String t = jwtService.gerar(u);
        tokens.add(t);
        return t;
    }

    private MvcResult abrir(String token) throws Exception {
        when(service.contarNaoLidas(any())).thenReturn(new ContagemNaoLidas(3));
        return mvc.perform(get(STREAM).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn();
    }

    private static List<String> eventos(MvcResult r) throws Exception {
        List<String> lista = new ArrayList<>();
        Matcher m = EVENTO.matcher(r.getResponse().getContentAsString());
        while (m.find()) {
            lista.add(m.group(1));
        }
        return lista;
    }

    private static NotificacaoDto dto(long id, String titulo) {
        return new NotificacaoDto(id, TipoNotificacao.DISTRIBUICAO, titulo, "Mensagem fictícia", 10L, false,
                LocalDateTime.now(), null);
    }

    /** Espera a thread do canal entregar (envio assíncrono). */
    private static void aguardar(MvcResult r, String trecho) throws Exception {
        long limite = System.currentTimeMillis() + 3000;
        while (!r.getResponse().getContentAsString().contains(trecho) && System.currentTimeMillis() < limite) {
            Thread.sleep(20);
        }
    }

    @Test
    void entregaSoAoDonoComEventoInicial() throws Exception {
        MvcResult a = abrir(token(Perfil.FISCAL, 701));
        MvcResult b = abrir(token(Perfil.GESTOR, 702));

        canal.aoCriar(new NotificacaoCriada(701L, dto(1, "Para o fiscal 701")));
        canal.aoCriar(new NotificacaoCriada(999L, dto(2, "Para outro usuario")));
        aguardar(a, "Para o fiscal 701");
        Thread.sleep(100);

        assertThat(a.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(eventos(a)).containsExactly("conectado", "notificacao");
        assertThat(a.getResponse().getContentAsString()).contains("\"naoLidas\":3")
                .contains("Para o fiscal 701").doesNotContain("Para outro usuario");
        assertThat(eventos(b)).containsExactly("conectado");
        assertThat(b.getResponse().getContentAsString()).doesNotContain("Para o fiscal 701");
    }

    @Test
    void keepaliveMantemOStream() throws Exception {
        MvcResult a = abrir(token(Perfil.CONSULTA, 703));
        aguardar(a, ":keepalive");
        assertThat(a.getResponse().getContentAsString()).contains(":keepalive");
    }

    @Test
    void semTokenOuComTokenSoNaQueryString401() throws Exception {
        String t = token(Perfil.ADMIN, 704);
        mvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());
        mvc.perform(get(STREAM + "?token=" + t + "&access_token=" + t).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verify(service, never()).contarNaoLidas(any());
        assertThat(canal.abertasDoUsuario(704L)).isZero();
    }

    @Test
    void limitePorUsuarioFechaOStreamMaisAntigo() throws Exception {
        String t1 = token(Perfil.ANALISTA, 705);
        MvcResult primeiro = abrir(t1);
        abrir(t1);
        abrir(t1);

        assertThat(canal.abertasDoUsuario(705L)).isEqualTo(2);
        assertThat(primeiro.getAsyncResult(2000)).isNull(); // stream encerrado pelo servidor
    }

    @Test
    void acimaDoTotalResponde503ProblemJson() throws Exception {
        for (long id = 710; id < 714; id++) {
            abrir(token(Perfil.FISCAL, id));
        }
        assertThat(canal.abertas()).isEqualTo(4);

        mvc.perform(get(STREAM).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(Perfil.FISCAL, 714))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(canal.abertas()).isEqualTo(4);
    }

    @Test
    void logoutEncerraOsStreamsDoToken() throws Exception {
        String t = token(Perfil.GESTOR, 720);
        MvcResult r = abrir(t);
        String outro = token(Perfil.GESTOR, 721);
        abrir(outro);

        assertThat(canal.encerrarDoToken(jwtService.validar(t).orElseThrow().jti())).isEqualTo(1);
        assertThat(r.getAsyncResult(2000)).isNull();
        assertThat(canal.abertasDoUsuario(720L)).isZero();
        assertThat(canal.abertasDoUsuario(721L)).isEqualTo(1);
    }

    @Test
    void aberturasConcorrentesRespeitamLimiteSemConcurrentModification() throws Exception {
        CanalNotificacoes concorrente = new CanalNotificacoes(
                Duration.ofMinutes(5), Duration.ofSeconds(30), 2, 100, Clock.systemUTC());
        int threads = 8;
        int porThread = 10;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            List<java.util.concurrent.Future<Void>> tarefas = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                tarefas.add(pool.submit(() -> {
                    for (int i = 0; i < porThread; i++) {
                        concorrente.abrir(800L, "jti-" + i, null, 0L);
                    }
                    return null;
                }));
            }
            for (var f : tarefas) {
                f.get();
            }
            assertThat(concorrente.abertasDoUsuario(800L)).isLessThanOrEqualTo(2);
        } finally {
            pool.shutdownNow();
            concorrente.destroy();
        }
    }
}
