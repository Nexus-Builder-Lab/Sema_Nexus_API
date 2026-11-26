package br.com.nexusbuilderlab.semanexus.core.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoria.Registro;
import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoriaRepositorio.Estado;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/** Selagem (Issue #148): encadeamento em ordem, continuação do estado e trava antes de ler os pendentes. */
class SeladorAuditoriaTest {

    CadeiaAuditoriaRepositorio cadeia = mock(CadeiaAuditoriaRepositorio.class);
    SeladorAuditoria selador = new SeladorAuditoria(cadeia, mock(PlatformTransactionManager.class));

    @Test
    void selaPendentesEmOrdemAPartirDaGeneseETravaOEstadoAntes() {
        Registro r1 = CadeiaAuditoriaTest.registro(11, "a");
        Registro r2 = CadeiaAuditoriaTest.registro(12, "b");
        when(cadeia.travarEstado()).thenReturn(new Estado(10, 0, null));
        when(cadeia.pendentes(10, SeladorAuditoria.LOTE)).thenReturn(List.of(r1, r2));
        when(cadeia.selar(anyLong(), anyLong(), anyString(), anyString())).thenReturn(true);

        assertThat(selador.selarPendentes()).isEqualTo(2);

        String h1 = CadeiaAuditoria.hash(1, CadeiaAuditoria.GENESE, r1);
        String h2 = CadeiaAuditoria.hash(2, h1, r2);
        var ordem = inOrder(cadeia);
        ordem.verify(cadeia).travarEstado();
        ordem.verify(cadeia).pendentes(10, SeladorAuditoria.LOTE);
        ordem.verify(cadeia).selar(11, 1, CadeiaAuditoria.GENESE, h1);
        ordem.verify(cadeia).selar(12, 2, h1, h2);
        ordem.verify(cadeia).atualizarEstado(2, h2);
        // A cadeia produzida é aceita pelo verificador.
        var v = new CadeiaAuditoria.Verificador();
        assertThat(v.aceitar(new CadeiaAuditoria.Selado(r1, 1, CadeiaAuditoria.GENESE, h1))).isTrue();
        assertThat(v.aceitar(new CadeiaAuditoria.Selado(r2, 2, h1, h2))).isTrue();
    }

    @Test
    void continuaDoUltimoHashDoEstado() {
        Registro r = CadeiaAuditoriaTest.registro(50, "c");
        String ultimo = "a".repeat(64);
        when(cadeia.travarEstado()).thenReturn(new Estado(10, 7, ultimo));
        when(cadeia.pendentes(10, SeladorAuditoria.LOTE)).thenReturn(List.of(r));
        when(cadeia.selar(anyLong(), anyLong(), anyString(), anyString())).thenReturn(true);

        selador.selarPendentes();

        verify(cadeia).selar(50, 8, ultimo, CadeiaAuditoria.hash(8, ultimo, r));
        verify(cadeia).atualizarEstado(eq(8L), anyString());
    }

    @Test
    void semPendentesNaoAlteraEstado() {
        when(cadeia.travarEstado()).thenReturn(new Estado(0, 3, "b".repeat(64)));
        when(cadeia.pendentes(0, SeladorAuditoria.LOTE)).thenReturn(List.of());

        assertThat(selador.selarPendentes()).isZero();
        verify(cadeia, never()).atualizarEstado(anyLong(), anyString());
    }

    @Test
    void registroJaSeladoNaoConsomePosicao() {
        Registro r1 = CadeiaAuditoriaTest.registro(1, "a");
        Registro r2 = CadeiaAuditoriaTest.registro(2, "b");
        when(cadeia.travarEstado()).thenReturn(new Estado(0, 0, null));
        when(cadeia.pendentes(anyLong(), anyInt())).thenReturn(List.of(r1, r2));
        when(cadeia.selar(eq(1L), anyLong(), anyString(), anyString())).thenReturn(false);
        when(cadeia.selar(eq(2L), anyLong(), anyString(), anyString())).thenReturn(true);

        selador.selarPendentes();

        verify(cadeia).selar(2, 1, CadeiaAuditoria.GENESE, CadeiaAuditoria.hash(1, CadeiaAuditoria.GENESE, r2));
        verify(cadeia).atualizarEstado(eq(1L), anyString());
    }
}
