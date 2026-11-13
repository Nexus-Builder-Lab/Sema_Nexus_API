package br.com.nexusbuilderlab.semanexus.core.equipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.EquipeDto;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.EquipeRequest;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.MembrosRequest;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.setor.SetorRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EquipeCadastroServiceTest {

    @Mock
    EquipeRepository equipes;
    @Mock
    SetorRepository setores;
    @Mock
    UsuarioRepository usuarios;
    @Mock
    AuditService auditoria;
    @InjectMocks
    EquipeService service;

    private static Setor setor(long id) {
        Setor s = new Setor("S" + id, "Setor " + id);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private static Usuario usuario(long id, boolean ativo) {
        Usuario u = new Usuario("U" + id, "u" + id + "@exemplo.test", "hash", Perfil.FISCAL);
        ReflectionTestUtils.setField(u, "id", id);
        u.setAtivo(ativo);
        return u;
    }

    private static Equipe equipe(long id, Setor setor) {
        Equipe e = new Equipe("Alfa", setor);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    @Test
    void criaEquipeComResponsavelComoMembroEAudita() {
        when(setores.findById(1L)).thenReturn(Optional.of(setor(1)));
        when(equipes.findBySetorIdAndNome(1L, "Alfa")).thenReturn(Optional.empty());
        when(usuarios.findById(3L)).thenReturn(Optional.of(usuario(3, true)));
        when(equipes.save(any())).thenAnswer(i -> i.getArgument(0));

        EquipeDto dto = service.criar(new EquipeRequest(" Alfa ", 1L, 3L, null));

        assertThat(dto.responsavel().id()).isEqualTo(3L);
        assertThat(dto.totalMembros()).isEqualTo(1);
        assertThat(dto.ativo()).isTrue();
        verify(auditoria).registrar(eq("CRIAR"), eq("EQUIPE"), any(), any());
    }

    @Test
    void nomeRepetidoNoSetorRetornaConflito() {
        when(setores.findById(1L)).thenReturn(Optional.of(setor(1)));
        when(equipes.findBySetorIdAndNome(1L, "Alfa")).thenReturn(Optional.of(equipe(9, setor(1))));
        assertThatThrownBy(() -> service.criar(new EquipeRequest("Alfa", 1L, null, null)))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void setorInexistenteOuResponsavelInativoRetorna400() {
        when(setores.findById(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.criar(new EquipeRequest("A", 8L, null, null)))
                .isInstanceOf(DadosInvalidosException.class);

        when(setores.findById(1L)).thenReturn(Optional.of(setor(1)));
        when(usuarios.findById(4L)).thenReturn(Optional.of(usuario(4, false)));
        assertThatThrownBy(() -> service.criar(new EquipeRequest("A", 1L, 4L, null)))
                .isInstanceOf(DadosInvalidosException.class);
    }

    @Test
    void definirMembrosSubstituiListaEMantemResponsavelSeContinuaMembro() {
        Equipe e = equipe(1, setor(1));
        Usuario u2 = usuario(2, true);
        e.getMembros().add(usuario(9, true));
        e.setResponsavel(u2);
        when(equipes.findById(1L)).thenReturn(Optional.of(e));
        when(usuarios.findById(2L)).thenReturn(Optional.of(u2));
        when(usuarios.findById(3L)).thenReturn(Optional.of(usuario(3, true)));

        EquipeDto dto = service.definirMembros(1L, new MembrosRequest(List.of(2L, 3L), null));

        assertThat(dto.totalMembros()).isEqualTo(2);
        assertThat(e.getMembros()).extracting(Usuario::getId).containsExactlyInAnyOrder(2L, 3L);
        assertThat(e.getResponsavel()).isSameAs(u2);
        verify(auditoria).registrar(eq("ATUALIZAR_MEMBROS"), eq("EQUIPE"), eq("1"), any());
    }

    @Test
    void definirMembrosRemoveResponsavelQueSaiuEAdicionaNovoResponsavel() {
        Equipe e = equipe(1, setor(1));
        e.setResponsavel(usuario(9, true));
        when(equipes.findById(1L)).thenReturn(Optional.of(e));
        when(usuarios.findById(2L)).thenReturn(Optional.of(usuario(2, true)));

        service.definirMembros(1L, new MembrosRequest(List.of(2L), null));
        assertThat(e.getResponsavel()).isNull();

        Usuario u5 = usuario(5, true);
        when(usuarios.findById(5L)).thenReturn(Optional.of(u5));
        service.definirMembros(1L, new MembrosRequest(List.of(2L), 5L));
        assertThat(e.getResponsavel()).isSameAs(u5);
        assertThat(e.getMembros()).hasSize(2);
    }

    @Test
    void membroInativoRetorna400EEquipeInexistente404() {
        Equipe e = equipe(1, setor(1));
        when(equipes.findById(1L)).thenReturn(Optional.of(e));
        when(usuarios.findById(4L)).thenReturn(Optional.of(usuario(4, false)));
        assertThatThrownBy(() -> service.definirMembros(1L, new MembrosRequest(List.of(4L), null)))
                .isInstanceOf(DadosInvalidosException.class);

        when(equipes.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.definirMembros(7L, new MembrosRequest(List.of(), null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void atualizarPermiteMesmoNomeDaPropriaEquipe() {
        Setor s = setor(1);
        Equipe e = equipe(1, s);
        when(equipes.findById(1L)).thenReturn(Optional.of(e));
        when(setores.findById(1L)).thenReturn(Optional.of(s));
        when(equipes.findBySetorIdAndNome(1L, "Alfa")).thenReturn(Optional.of(e));

        EquipeDto dto = service.atualizar(1L, new EquipeRequest("Alfa", 1L, null, false));

        assertThat(dto.ativo()).isFalse();
        assertThat(dto.responsavel()).isNull();
    }
}
