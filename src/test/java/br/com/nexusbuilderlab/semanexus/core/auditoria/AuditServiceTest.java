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

    @Mock
    SeladorAuditoria selador;

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

    @Test
    void foraDeTransacaoPedeSelagemNaHora() {
        servico.registrarComo(1L, "X", "Y", "1", null);

        verify(selador).solicitarSelagem();
    }

    @Test
    void emTransacaoPedeSelagemSoDepoisDoCommitENuncaNoRollback() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            servico.registrarComo(1L, "X", "Y", "1", null);
            servico.registrarComo(1L, "X", "Y", "2", null);
            org.mockito.Mockito.verifyNoInteractions(selador);

            var syncs = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            syncs.forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(selador, org.mockito.Mockito.times(2)).solicitarSelagem();
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }

        org.mockito.Mockito.clearInvocations(selador);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            servico.registrarComo(1L, "X", "Y", "3", null);
            // Rollback: só afterCompletion(ROLLED_BACK), sem afterCommit.
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(t -> t.afterCompletion(
                            org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            org.mockito.Mockito.verifyNoInteractions(selador);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void alteracaoGravaAntesEDepoisSoDosCamposAlteradosESaneados() {
        var principal = new UsuarioAutenticado(9L, "Fulano", "f@exemplo.test", Perfil.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        servico.registrarAlteracao("ATUALIZAR", "SETOR", "3", "Setor atualizado.", Alteracoes.nova()
                .campo("nome", "Antigo", "Novo")
                .campo("sigla", "ABC", "ABC")
                .campo("token", "abc123", "xyz789"));

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repositorio).save(captor.capture());
        Auditoria a = captor.getValue();
        assertThat(a.getUsuarioId()).isEqualTo(9L);
        assertThat(a.getValorAnterior()).contains("\"nome\":\"Antigo\"").doesNotContain("sigla", "abc123");
        assertThat(a.getValorNovo()).contains("\"nome\":\"Novo\"").doesNotContain("xyz789");
    }

    @Test
    void alteracaoSemCamposAlteradosGravaSoODetalhe() {
        servico.registrarAlteracao("ATUALIZAR", "SETOR", "3", "nada mudou",
                Alteracoes.nova().campo("nome", "Igual", "Igual"));

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repositorio).save(captor.capture());
        assertThat(captor.getValue().getValorAnterior()).isNull();
        assertThat(captor.getValue().getValorNovo()).isNull();
    }

    @Test
    void dtoExpoeAntesDepoisComoObjeto() {
        var a = new Auditoria(7L, "EDITAR", "DEMANDA", "1", "x", "{\"titulo\":\"A\"}", "{\"titulo\":\"B\"}");
        when(repositorio.findTop50ByEntidadeAndEntidadeIdOrderByDataDescIdDesc("DEMANDA", "1")).thenReturn(List.of(a));

        var dto = servico.historicoDaEntidade("DEMANDA", "1").get(0);

        assertThat(dto.valorAnterior()).containsEntry("titulo", "A");
        assertThat(dto.valorNovo()).containsEntry("titulo", "B");
        assertThat(dto.hashRegistro()).isNull();
    }

    @Test
    void alteracaoSaneiaSegredosAntesDeTruncarAoLimite() throws Exception {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOjF9.assinaturaMuitoSecreta";
        Alteracoes alt = Alteracoes.nova()
                .campo("token", "a", "b")
                .campo("observacao", "ok", "x".repeat(295) + " " + jwt);
        for (int i = 0; i < 40; i++) {
            alt.campo("campo" + i, "v".repeat(200), "w".repeat(200));
        }

        servico.registrarAlteracao("ATUALIZAR", "DEMANDA", "10", "ç".repeat(3000), alt);

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(repositorio).save(captor.capture());
        Auditoria a = captor.getValue();
        assertThat(a.getDetalhe().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(AuditService.LIMITE_DETALHE);
        assertThat(a.getValorAnterior()).doesNotContain("\"a\"");
        assertThat(a.getValorNovo()).doesNotContain("\"b\"", "eyJhbGci", "assinaturaMuitoSecreta");
        assertThat(a.getValorAnterior().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(Alteracoes.LIMITE_BYTES);
        assertThat(a.getValorNovo().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(Alteracoes.LIMITE_BYTES);
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(a.getValorNovo());
    }
}
