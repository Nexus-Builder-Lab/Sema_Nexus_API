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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** Revogação de JWT no logout (Issue #134): gravação idempotente, consulta com cache e limpeza dos expirados. */
@ExtendWith(MockitoExtension.class)
class RevogacaoTokenServiceTest {

    static final Instant AGORA = Instant.parse("2026-11-24T12:00:00Z");
    static final Instant EXPIRA = AGORA.plusSeconds(3600);

    @Mock
    TokenRevogadoRepository repositorio;

    RevogacaoTokenService servico;

    @BeforeEach
    void setUp() {
        servico = new RevogacaoTokenService(repositorio, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void revogarGravaJtiComExpiracaoEPassaAValerNaHora() {
        when(repositorio.existsById("jti-1")).thenReturn(false);

        servico.revogar("jti-1", 5L, EXPIRA);

        ArgumentCaptor<TokenRevogado> gravado = ArgumentCaptor.forClass(TokenRevogado.class);
        verify(repositorio).saveAndFlush(gravado.capture());
        assertThat(gravado.getValue().getJti()).isEqualTo("jti-1");
        assertThat(gravado.getValue().getUsuarioId()).isEqualTo(5L);
        assertThat(gravado.getValue().getExpiraEm()).isEqualTo(LocalDateTime.ofInstant(EXPIRA, ZoneOffset.UTC));
        // Cache local: não consulta o banco para um revogado já conhecido.
        assertThat(servico.revogado("jti-1")).isTrue();
        verify(repositorio, never()).findById("jti-1");
    }

    @Test
    void revogarDeNovoOuEmParaleloNaoFalha() {
        when(repositorio.existsById("jti-2")).thenReturn(true);
        servico.revogar("jti-2", 5L, EXPIRA);
        verify(repositorio, never()).saveAndFlush(any());

        when(repositorio.existsById("jti-3")).thenReturn(false);
        when(repositorio.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("PK"));
        servico.revogar("jti-3", 5L, EXPIRA);
        assertThat(servico.revogado("jti-3")).isTrue();
    }

    @Test
    void consultaOBancoParaTokenDesconhecido() {
        when(repositorio.findById("de-outra-instancia")).thenReturn(Optional.of(
                new TokenRevogado("de-outra-instancia", 1L, LocalDateTime.ofInstant(EXPIRA, ZoneOffset.UTC),
                        LocalDateTime.ofInstant(AGORA, ZoneOffset.UTC))));
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
        when(repositorio.removerExpirados(LocalDateTime.ofInstant(AGORA, ZoneOffset.UTC))).thenReturn(3);

        assertThat(servico.limparExpirados()).isEqualTo(3);
    }
}
