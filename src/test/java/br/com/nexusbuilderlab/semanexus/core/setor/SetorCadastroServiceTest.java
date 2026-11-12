package br.com.nexusbuilderlab.semanexus.core.setor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.setor.dto.SetorRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SetorCadastroServiceTest {

    @Mock
    SetorRepository setores;
    @Mock
    AuditService auditoria;
    @InjectMocks
    SetorService service;

    private static Setor setor(long id, String sigla) {
        Setor s = new Setor(sigla, "Setor " + sigla);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    @Test
    void criaSetorAtivoComSiglaMaiusculaEAudita() {
        when(setores.findBySigla("ABC")).thenReturn(Optional.empty());
        when(setores.save(any())).thenAnswer(i -> i.getArgument(0));

        var dto = service.criar(new SetorRequest(" abc ", " Nome ", null));

        assertThat(dto.sigla()).isEqualTo("ABC");
        assertThat(dto.nome()).isEqualTo("Nome");
        assertThat(dto.ativo()).isTrue();
        verify(auditoria).registrar(eq("CRIAR"), eq("SETOR"), any(), any());
    }

    @Test
    void siglaDuplicadaRetornaConflito() {
        when(setores.findBySigla("ABC")).thenReturn(Optional.of(setor(1, "ABC")));
        assertThatThrownBy(() -> service.criar(new SetorRequest("abc", "N", null)))
                .isInstanceOf(ConflitoException.class);
        verify(setores, never()).save(any());
    }

    @Test
    void atualizaMantendoSituacaoQuandoAtivoAusenteEPermitindoMesmaSigla() {
        Setor s = setor(1, "ABC");
        when(setores.findById(1L)).thenReturn(Optional.of(s));
        when(setores.findBySigla("ABC")).thenReturn(Optional.of(s));

        var dto = service.atualizar(1L, new SetorRequest("ABC", "Outro", null));

        assertThat(dto.nome()).isEqualTo("Outro");
        assertThat(dto.ativo()).isTrue();
        verify(auditoria).registrar(eq("ATUALIZAR"), eq("SETOR"), eq("1"), any());
    }

    @Test
    void atualizaInativando() {
        Setor s = setor(1, "ABC");
        when(setores.findById(1L)).thenReturn(Optional.of(s));
        assertThat(service.atualizar(1L, new SetorRequest("ABC", "N", false)).ativo()).isFalse();
    }

    @Test
    void siglaDeOutroSetorRetornaConflitoEInexistente404() {
        when(setores.findById(1L)).thenReturn(Optional.of(setor(1, "ABC")));
        when(setores.findBySigla("XYZ")).thenReturn(Optional.of(setor(2, "XYZ")));
        assertThatThrownBy(() -> service.atualizar(1L, new SetorRequest("XYZ", "N", null)))
                .isInstanceOf(ConflitoException.class);
        when(setores.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.atualizar(5L, new SetorRequest("Q", "N", null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
