package br.com.nexusbuilderlab.semanexus.core.setor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.core.setor.dto.SetorDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SetorServiceTest {

    @Mock
    SetorRepository setores;
    @InjectMocks
    SetorService service;

    private static Setor setor(long id, String sigla, boolean ativo) {
        Setor s = new Setor(sigla, "Setor " + sigla);
        ReflectionTestUtils.setField(s, "id", id);
        s.setAtivo(ativo);
        return s;
    }

    @Test
    void semFiltroTrazTodosPorSigla() {
        when(setores.findAllByOrderBySiglaAsc()).thenReturn(List.of(setor(1, "A", true), setor(2, "B", false)));

        List<SetorDto> lista = service.listar(null);

        assertThat(lista).extracting(SetorDto::sigla).containsExactly("A", "B");
        assertThat(lista).extracting(SetorDto::ativo).containsExactly(true, false);
        assertThat(lista.get(0).nome()).isEqualTo("Setor A");
    }

    @Test
    void comFiltroDeSituacaoUsaOFiltroDoRepositorio() {
        when(setores.findByAtivoOrderBySiglaAsc(true)).thenReturn(List.of(setor(1, "A", true)));

        assertThat(service.listar(true)).extracting(SetorDto::sigla).containsExactly("A");
        verify(setores).findByAtivoOrderBySiglaAsc(true);
    }
}
