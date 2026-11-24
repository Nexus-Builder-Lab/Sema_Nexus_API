package br.com.nexusbuilderlab.semanexus.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** Revogação de JWT no logout (Issue #134): gravação idempotente, colisão concorrente, cache e limpeza. */
@ExtendWith(MockitoExtension.class)
class RevogacaoTokenServiceTest {

    static final Instant AGORA = Instant.parse("2026-11-24T12:00:00Z");
    static final Instant EXPIRA = AGORA.plusSeconds(3600);
    static final LocalDateTime EXPIRA_UTC = LocalDateTime.ofInstant(EXPIRA, ZoneOffset.UTC);
    static final LocalDateTime AGORA_UTC = LocalDateTime.ofInstant(AGORA, ZoneOffset.UTC);

    @Mock
    TokenRevogadoRepository repositorio;
    @Mock
    GravadorTokenRevogado gravador;

    RevogacaoTokenService servico;

    @BeforeEach
    void setUp() {
        servico = new RevogacaoTokenService(repositorio, gravador, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void revogarGravaJtiComExpiracaoEPassaAValerNaHora() {
        when(gravador.gravar("jti-1", 5L, EXPIRA_UTC, AGORA_UTC)).thenReturn(true);

        assertThat(servico.revogar("jti-1", 5L, EXPIRA)).isTrue();

        // Cache local: não consulta o banco para um revogado já conhecido.
        assertThat(servico.revogado("jti-1")).isTrue();
        verify(repositorio, never()).findById("jti-1");
    }

    @Test
    void revogarDeNovoDevolveFalseSemFalhar() {
        when(gravador.gravar("jti-2", 5L, EXPIRA_UTC, AGORA_UTC)).thenReturn(false);

        assertThat(servico.revogar("jti-2", 5L, EXPIRA)).isFalse();
        assertThat(servico.revogado("jti-2")).isTrue();
    }

    @Test
    void colisaoConcorrenteNaPkContaComoJaRevogadoSemPropagarErro() {
        when(gravador.gravar(any(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("ORA-00001"));

        assertThat(servico.revogar("jti-3", 5L, EXPIRA)).isFalse();
        assertThat(servico.revogado("jti-3")).isTrue();
    }

    @Test
    void semDadosDoTokenNaoGrava() {
        assertThat(servico.revogar(null, 5L, EXPIRA)).isFalse();
        assertThat(servico.revogar("jti", 5L, null)).isFalse();
        verify(gravador, never()).gravar(any(), any(), any(), any());
    }

    @Test
    void consultaOBancoParaTokenDesconhecido() {
        when(repositorio.findById("de-outra-instancia")).thenReturn(Optional.of(
                new TokenRevogado("de-outra-instancia", 1L, EXPIRA_UTC, AGORA_UTC)));
        when(repositorio.findById("valido")).thenReturn(Optional.empty());

        assertThat(servico.revogado("de-outra-instancia")).isTrue();
        assertThat(servico.revogado("de-outra-instancia")).isTrue();
        assertThat(servico.revogado("valido")).isFalse();
        verify(repositorio, times(1)).findById("de-outra-instancia");
    }

    @Test
    void jtiVazioContaComoRevogado() {
        assertThat(servico.revogado(null)).isTrue();
        assertThat(servico.revogado(" ")).isTrue();
    }

    @Test
    void limpezaRemoveExpiradosPelaDataAtual() {
        when(repositorio.removerExpirados(AGORA_UTC)).thenReturn(3);

        assertThat(servico.limparExpirados()).isEqualTo(3);
    }
}
