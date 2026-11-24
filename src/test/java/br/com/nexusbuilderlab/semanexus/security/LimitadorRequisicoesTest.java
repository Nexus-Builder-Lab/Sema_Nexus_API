package br.com.nexusbuilderlab.semanexus.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nexusbuilderlab.semanexus.security.LimitadorRequisicoes.Categoria;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Limite de requisições em janela fixa (Issue #134) e regras de categoria/IP do filtro. */
class LimitadorRequisicoesTest {

    /** Relógio ajustável para simular a passagem do tempo. */
    static final class Relogio extends Clock {
        Instant agora = Instant.parse("2026-11-24T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    @Test
    void bloqueiaAcimaDoLimiteComRetryAfterAteAProximaJanela() {
        Relogio relogio = new Relogio();
        LimitadorRequisicoes limitador = new LimitadorRequisicoes(true, 2, 5, 5, Duration.ofSeconds(60), relogio);

        assertThat(limitador.consumir(Categoria.LOGIN, "ip:1").permitido()).isTrue();
        relogio.agora = relogio.agora.plusSeconds(15);
        assertThat(limitador.consumir(Categoria.LOGIN, "ip:1").permitido()).isTrue();
        var bloqueado = limitador.consumir(Categoria.LOGIN, "ip:1");
        assertThat(bloqueado.permitido()).isFalse();
        assertThat(bloqueado.retryAfterSegundos()).isEqualTo(45);
        // Outra chave e outra categoria têm contadores próprios.
        assertThat(limitador.consumir(Categoria.LOGIN, "ip:2").permitido()).isTrue();
        assertThat(limitador.consumir(Categoria.IA, "ip:1").permitido()).isTrue();

        relogio.agora = relogio.agora.plusSeconds(45);
        assertThat(limitador.consumir(Categoria.LOGIN, "ip:1").permitido()).isTrue();
    }

    @Test
    void desativadoNuncaBloqueia() {
        LimitadorRequisicoes limitador = new LimitadorRequisicoes(false, 1, 1, 1, Duration.ofSeconds(60), new Relogio());
        for (int i = 0; i < 5; i++) {
            assertThat(limitador.consumir(Categoria.UPLOAD, "u:1").permitido()).isTrue();
        }
    }

    @Test
    void categoriasCobremLoginIaEUploadSomenteEmPost() {
        assertThat(LimiteRequisicoesFilter.categoria("POST", "/api/auth/login")).isEqualTo(Categoria.LOGIN);
        assertThat(LimiteRequisicoesFilter.categoria("POST", "/api/auth/demo/ADMIN")).isEqualTo(Categoria.LOGIN);
        assertThat(LimiteRequisicoesFilter.categoria("POST", "/api/demandas/12/triagem/sugestao"))
                .isEqualTo(Categoria.IA);
        assertThat(LimiteRequisicoesFilter.categoria("POST", "/api/docia/perguntas")).isEqualTo(Categoria.IA);
        assertThat(LimiteRequisicoesFilter.categoria("POST", "/api/documentos")).isEqualTo(Categoria.UPLOAD);
        assertThat(LimiteRequisicoesFilter.categoria("GET", "/api/documentos")).isNull();
        assertThat(LimiteRequisicoesFilter.categoria("POST", "/api/documentos/comparacao")).isNull();
        assertThat(LimiteRequisicoesFilter.categoria("POST", "/api/demandas/12/triagem")).isNull();
        assertThat(LimiteRequisicoesFilter.categoria("POST", "/api/auth/logout")).isNull();
    }

    @Test
    void ipDoClienteSoUsaXForwardedForVindoDeProxyConfigurado() {
        ProxiesConfiaveis proxies = new ProxiesConfiaveis("172.18.0.5, 10.9.0.0/16", Clock.systemUTC());

        MockHttpServletRequest viaNginx = new MockHttpServletRequest();
        viaNginx.setRemoteAddr("172.18.0.5");
        viaNginx.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.9");
        assertThat(LimiteRequisicoesFilter.ipCliente(viaNginx, proxies)).isEqualTo("203.0.113.9");

        MockHttpServletRequest daFaixa = new MockHttpServletRequest();
        daFaixa.setRemoteAddr("10.9.200.1");
        daFaixa.addHeader("X-Forwarded-For", "203.0.113.10");
        assertThat(LimiteRequisicoesFilter.ipCliente(daFaixa, proxies)).isEqualTo("203.0.113.10");

        MockHttpServletRequest lixo = new MockHttpServletRequest();
        lixo.setRemoteAddr("127.0.0.1");
        lixo.addHeader("X-Forwarded-For", "nao-e-ip");
        assertThat(LimiteRequisicoesFilter.ipCliente(lixo, proxies)).isEqualTo("127.0.0.1");
    }

    @Test
    void xForwardedForForjadoDeOrigemNaoConfiavelEIgnorado() {
        ProxiesConfiaveis proxies = new ProxiesConfiaveis("172.18.0.5", Clock.systemUTC());

        // Outro endereço privado (ex.: gateway do Docker publicando a porta, ou outro container): não é o proxy.
        MockHttpServletRequest privadoNaoConfigurado = new MockHttpServletRequest();
        privadoNaoConfigurado.setRemoteAddr("172.17.0.1");
        privadoNaoConfigurado.addHeader("X-Forwarded-For", "198.51.100.99");
        assertThat(LimiteRequisicoesFilter.ipCliente(privadoNaoConfigurado, proxies)).isEqualTo("172.17.0.1");

        MockHttpServletRequest externo = new MockHttpServletRequest();
        externo.setRemoteAddr("198.51.100.7");
        externo.addHeader("X-Forwarded-For", "10.0.0.1");
        assertThat(LimiteRequisicoesFilter.ipCliente(externo, proxies)).isEqualTo("198.51.100.7");

        // Sem configuração: só o loopback é confiável.
        ProxiesConfiaveis padrao = ProxiesConfiaveis.somenteLoopback();
        assertThat(padrao.confiavel("10.0.0.2")).isFalse();
        assertThat(padrao.confiavel("127.0.0.1")).isTrue();
        assertThat(padrao.confiavel("::1")).isTrue();
    }

    @Test
    void forjarXForwardedForNaoEscapaDoLimiteDeLogin() {
        ProxiesConfiaveis proxies = new ProxiesConfiaveis("172.18.0.5", Clock.systemUTC());
        LimitadorRequisicoes limitador = new LimitadorRequisicoes(true, 2, 5, 5, Duration.ofSeconds(60), new Relogio());
        int permitidos = 0;
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest r = new MockHttpServletRequest();
            r.setRemoteAddr("172.17.0.1");
            r.addHeader("X-Forwarded-For", "203.0.113." + i);
            if (limitador.consumir(Categoria.LOGIN, "ip:" + LimiteRequisicoesFilter.ipCliente(r, proxies)).permitido()) {
                permitidos++;
            }
        }
        assertThat(permitidos).isEqualTo(2);
    }

    @Test
    void proxyPorNomeDeHostEItensInvalidos() {
        ProxiesConfiaveis proxies = new ProxiesConfiaveis("localhost, 999.1.1.1/40, ../x", Clock.systemUTC());
        assertThat(proxies.confiavel("127.0.0.1")).isTrue();
        assertThat(proxies.confiavel("203.0.113.1")).isFalse();
        assertThat(proxies.confiavel("nao-e-ip")).isFalse();
        assertThat(proxies.confiavel(null)).isFalse();
    }

    @Test
    void chavesVivasTemTetoEVencidasSaemSemVarreduraPorRequisicao() {
        Relogio relogio = new Relogio();
        LimitadorRequisicoes limitador = new LimitadorRequisicoes(true, 1, 1, 1, Duration.ofSeconds(60), 100, relogio);
        for (int i = 0; i < 250; i++) {
            limitador.consumir(Categoria.LOGIN, "ip:" + i);
        }
        assertThat(limitador.chavesVivas()).isEqualTo(100);
        // A mais recente continua contada; a mais antiga foi descartada.
        assertThat(limitador.consumir(Categoria.LOGIN, "ip:249").permitido()).isFalse();

        relogio.agora = relogio.agora.plusSeconds(61);
        limitador.consumir(Categoria.LOGIN, "ip:novo");
        assertThat(limitador.chavesVivas()).isEqualTo(1);
    }
}
