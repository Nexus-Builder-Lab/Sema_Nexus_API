package br.com.nexusbuilderlab.semanexus.core.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoria.Registro;
import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoria.Selado;
import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoriaRepositorio.Estado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class IntegridadeAuditoriaServiceTest {

    CadeiaAuditoriaRepositorio cadeia = mock(CadeiaAuditoriaRepositorio.class);
    IntegridadeAuditoriaService servico = new IntegridadeAuditoriaService(cadeia,
            Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC));

    @SuppressWarnings("unchecked")
    private void comCadeia(List<Selado> selados, Estado estado) {
        when(cadeia.estado()).thenReturn(estado);
        when(cadeia.contarLegados(estado.inicioId())).thenReturn(4L);
        when(cadeia.contarPendentes(estado.inicioId())).thenReturn(1L);
        doAnswer(inv -> {
            long ate = inv.getArgument(0);
            Predicate<Selado> consumidor = inv.getArgument(1);
            for (Selado s : selados) {
                if (s.seq() <= ate && !consumidor.test(s)) {
                    break;
                }
            }
            return null;
        }).when(cadeia).percorrerSelados(eq(estado.ultimaSeq()), any(Predicate.class));
    }

    @Test
    void cadeiaIntegra() {
        List<Selado> c = CadeiaAuditoriaTest.cadeia(6);
        comCadeia(c, new Estado(4, 6, c.get(5).hashRegistro()));

        var r = servico.verificar();

        assertThat(r.integra()).isTrue();
        assertThat(r.situacao()).isEqualTo("INTEGRA");
        assertThat(r.algoritmo()).isEqualTo("SHA-256");
        assertThat(r.registrosVerificados()).isEqualTo(6);
        assertThat(r.registrosLegados()).isEqualTo(4);
        assertThat(r.registrosPendentes()).isEqualTo(1);
        assertThat(r.ultimaSequencia()).isEqualTo(6);
        assertThat(r.primeiraFalha()).isNull();
    }

    @Test
    void cadeiaAdulteradaApontaAPrimeiraFalha() {
        List<Selado> c = new ArrayList<>(CadeiaAuditoriaTest.cadeia(6));
        Selado s = c.get(3);
        Registro r = s.registro();
        c.set(3, new Selado(new Registro(r.id(), r.data(), 999L, r.acao(), r.entidade(), r.entidadeId(),
                r.detalhe(), r.valorAnterior(), r.valorNovo()), s.seq(), s.hashAnterior(), s.hashRegistro()));
        comCadeia(c, new Estado(0, 6, c.get(5).hashRegistro()));

        var res = servico.verificar();

        assertThat(res.integra()).isFalse();
        assertThat(res.situacao()).isEqualTo("VIOLADA");
        assertThat(res.primeiraFalha().motivo()).isEqualTo("HASH_DIVERGENTE");
        assertThat(res.primeiraFalha().sequencia()).isEqualTo(4);
        assertThat(res.primeiraFalha().auditoriaId()).isEqualTo(r.id());
        assertThat(res.primeiraFalha().descricao()).isNotBlank();
    }

    @Test
    void selagensConcorrentesAlemDoEstadoLidoNaoGeramFalsoAlarme() {
        // O estado lido diz 4; a consulta só percorre até a seq 4, mesmo com 6 já seladas no banco.
        List<Selado> c = CadeiaAuditoriaTest.cadeia(6);
        comCadeia(c, new Estado(0, 4, c.get(3).hashRegistro()));

        var r = servico.verificar();
        assertThat(r.integra()).isTrue();
        assertThat(r.registrosVerificados()).isEqualTo(4);
    }
}
