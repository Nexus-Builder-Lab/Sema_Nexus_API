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
    void ipDoClienteSoUsaXForwardedForVindoDeProxyPrivado() {
        MockHttpServletRequest viaNginx = new MockHttpServletRequest();
        viaNginx.setRemoteAddr("172.18.0.5");
        viaNginx.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.9");
        assertThat(LimiteRequisicoesFilter.ipCliente(viaNginx)).isEqualTo("203.0.113.9");

        MockHttpServletRequest direto = new MockHttpServletRequest();
        direto.setRemoteAddr("198.51.100.7");
        direto.addHeader("X-Forwarded-For", "10.0.0.1");
        assertThat(LimiteRequisicoesFilter.ipCliente(direto)).isEqualTo("198.51.100.7");

        MockHttpServletRequest lixo = new MockHttpServletRequest();
        lixo.setRemoteAddr("127.0.0.1");
        lixo.addHeader("X-Forwarded-For", "nao-e-ip");
        assertThat(LimiteRequisicoesFilter.ipCliente(lixo)).isEqualTo("127.0.0.1");
    }
}
