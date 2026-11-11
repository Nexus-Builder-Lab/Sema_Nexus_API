package br.com.nexusbuilderlab.semanexus.core.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Mock
    br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository usuarios;

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
    void detalheNaoContemSenhasNemTokens() {
        servico.registrarComo(1L, "LOGIN", "USUARIO", "1",
                "senha=12345678 token: abc.def Authorization: Bearer xyz123 jwt eyJhbGciOi.eyJzdWIiOjF9.assinatura ok");

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repositorio).save(captor.capture());
        String d = captor.getValue().getDetalhe();
        assertThat(d).doesNotContain("12345678", "abc.def", "xyz123", "eyJ").contains("***").endsWith("ok");
    }

    @Test
    void consultaAplicaFiltrosEOrdemPadraoDecrescente() {
        var a = new Auditoria(7L, "CRIAR", "DEMANDA", "1", "x");
        var u = new br.com.nexusbuilderlab.semanexus.core.usuario.Usuario(
                "Ana", "ana@exemplo.test", "hash", Perfil.ADMIN);
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", 7L);
        when(repositorio.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(a)));
        when(usuarios.findAllById(any())).thenReturn(List.of(u));

        var r = servico.consultar(new br.com.nexusbuilderlab.semanexus.core.auditoria.dto.AuditoriaFiltro(
                java.time.LocalDate.now(), null, 7L, "criar", null),
                org.springframework.data.domain.PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("naoExiste")));

        assertThat(r.content()).hasSize(1);
        assertThat(r.content().get(0).usuarioNome()).isEqualTo("Ana");
        ArgumentCaptor<org.springframework.data.domain.Pageable> p =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(repositorio).findAll(any(org.springframework.data.jpa.domain.Specification.class), p.capture());
        assertThat(p.getValue().getSort().getOrderFor("data").isDescending()).isTrue();
    }

    @Test
    void recentesLimitaEOrdena() {
        when(repositorio.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        List.of(new Auditoria(null, "X", "Y", "1", null))));

        assertThat(servico.recentes(500)).hasSize(1);
        ArgumentCaptor<org.springframework.data.domain.Pageable> p =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(repositorio).findAll(p.capture());
        assertThat(p.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void detalheLongoETruncadoAoLimiteDaColuna() {
        servico.registrarComo(1L, "X", "Y", "1", "a".repeat(5000));

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repositorio).save(captor.capture());
        assertThat(captor.getValue().getDetalhe()).hasSize(AuditService.LIMITE_DETALHE);
    }
}
