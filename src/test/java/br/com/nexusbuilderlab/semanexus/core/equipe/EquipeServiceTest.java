package br.com.nexusbuilderlab.semanexus.core.equipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.EquipeDto;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.MembroDto;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EquipeServiceTest {

    @Mock
    EquipeRepository equipes;
    @InjectMocks
    EquipeService service;

    private static Setor setor(long id, String sigla) {
        Setor s = new Setor(sigla, "Setor " + sigla);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private static Usuario usuario(long id, String nome, Perfil perfil) {
        Usuario u = new Usuario(nome, nome.toLowerCase().replace(' ', '.') + "@exemplo.test", "hash-secreto", perfil);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Equipe equipe(long id, String nome, Setor setor, Usuario responsavel, boolean ativo) {
        Equipe e = new Equipe(nome, setor);
        ReflectionTestUtils.setField(e, "id", id);
        e.setResponsavel(responsavel);
        e.setAtivo(ativo);
        return e;
    }

    private static EquipeRepository.ContagemMembros contagem(long equipeId, long total) {
        return new EquipeRepository.ContagemMembros() {
            @Override
            public Long getEquipeId() {
                return equipeId;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }

    private void umaEstruturaDeTeste() {
        Setor fisc = setor(1, "FISC");
        Setor doc = setor(2, "DOC");
        Usuario chefe = usuario(10, "Chefe Teste", Perfil.GESTOR);
        when(equipes.findAllComSetorEResponsavel()).thenReturn(List.of(
                equipe(1, "Alfa", fisc, chefe, true),
                equipe(2, "Beta", fisc, null, false),
                equipe(3, "Gama", doc, null, true)));
        when(equipes.contarMembrosPorEquipe()).thenReturn(List.of(contagem(1, 3), contagem(2, 0)));
    }

    @Test
    void listaEquipesComSetorResponsavelETotalDeMembros() {
        umaEstruturaDeTeste();

        List<EquipeDto> lista = service.listar(null, null);

        assertThat(lista).extracting(EquipeDto::nome).containsExactly("Alfa", "Beta", "Gama");
        EquipeDto alfa = lista.get(0);
        assertThat(alfa.setorId()).isEqualTo(1L);
        assertThat(alfa.setorSigla()).isEqualTo("FISC");
        assertThat(alfa.totalMembros()).isEqualTo(3);
        assertThat(alfa.responsavel().nome()).isEqualTo("Chefe Teste");
        assertThat(lista.get(1).responsavel()).isNull();
        // Equipe ausente da contagem (sem membros) aparece com 0.
        assertThat(lista.get(2).totalMembros()).isZero();
    }

    @Test
    void filtraPorSetor() {
        umaEstruturaDeTeste();

        assertThat(service.listar(1L, null)).extracting(EquipeDto::nome).containsExactly("Alfa", "Beta");
        assertThat(service.listar(2L, null)).extracting(EquipeDto::nome).containsExactly("Gama");
        assertThat(service.listar(99L, null)).isEmpty();
    }

    @Test
    void filtraPorSituacaoECombinaFiltros() {
        umaEstruturaDeTeste();

        assertThat(service.listar(null, true)).extracting(EquipeDto::nome).containsExactly("Alfa", "Gama");
        assertThat(service.listar(null, false)).extracting(EquipeDto::nome).containsExactly("Beta");
        assertThat(service.listar(1L, true)).extracting(EquipeDto::nome).containsExactly("Alfa");
    }

    @Test
    void membrosDaEquipeNaoExpoeSenhaOuHash() {
        when(equipes.existsById(1L)).thenReturn(true);
        when(equipes.findMembros(1L)).thenReturn(List.of(
                usuario(3, "Fiscal Um", Perfil.FISCAL), usuario(4, "Fiscal Dois", Perfil.FISCAL)));

        List<MembroDto> membros = service.membros(1L);

        assertThat(membros).extracting(MembroDto::nome).containsExactly("Fiscal Um", "Fiscal Dois");
        assertThat(membros.get(0).perfil()).isEqualTo(Perfil.FISCAL);
        assertThat(membros.get(0).email()).isEqualTo("fiscal.um@exemplo.test");
        assertThat(membros.toString()).doesNotContain("hash-secreto");
    }

    @Test
    void membrosDeEquipeInexistenteLancaNaoEncontrado() {
        when(equipes.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.membros(404L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Equipe 404");
        verify(equipes, never()).findMembros(404L);
    }
}
