package br.com.nexusbuilderlab.semanexus.core.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    AuditoriaRepository repositorio;

    @InjectMocks
    AuditService servico;

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registraEmNomeDoUsuarioDoContexto() {
        var principal = new UsuarioAutenticado(9L, "Fulano", "f@exemplo.test", Perfil.FISCAL);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        servico.registrar("CRIAR", "DEMANDA", "42", "detalhe");

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repositorio).save(captor.capture());
        Auditoria a = captor.getValue();
        assertThat(a.getUsuarioId()).isEqualTo(9L);
        assertThat(a.getAcao()).isEqualTo("CRIAR");
        assertThat(a.getEntidade()).isEqualTo("DEMANDA");
        assertThat(a.getEntidadeId()).isEqualTo("42");
    }

    @Test
    void semContextoRegistraSemUsuario() {
        servico.registrar("LIMPEZA", "SISTEMA", null, null);

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repositorio).save(captor.capture());
        assertThat(captor.getValue().getUsuarioId()).isNull();
    }

    @Test
    void detalheLongoETruncadoAoLimiteDaColuna() {
        servico.registrarComo(1L, "X", "Y", "1", "a".repeat(5000));

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repositorio).save(captor.capture());
        assertThat(captor.getValue().getDetalhe()).hasSize(AuditService.LIMITE_DETALHE);
    }
}
