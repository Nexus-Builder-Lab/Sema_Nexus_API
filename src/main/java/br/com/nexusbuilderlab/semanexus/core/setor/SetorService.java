package br.com.nexusbuilderlab.semanexus.core.setor;

import br.com.nexusbuilderlab.semanexus.core.setor.dto.SetorDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de setores (cadastro e edição são P1, Issue #24). */
@Service
public class SetorService {

    private final SetorRepository setores;

    public SetorService(SetorRepository setores) {
        this.setores = setores;
    }

    /** Setores por sigla; {@code ativo == null} traz todos. */
    @Transactional(readOnly = true)
    public List<SetorDto> listar(Boolean ativo) {
        var lista = ativo == null ? setores.findAllByOrderBySiglaAsc() : setores.findByAtivoOrderBySiglaAsc(ativo);
        return lista.stream().map(SetorDto::de).toList();
    }
}
