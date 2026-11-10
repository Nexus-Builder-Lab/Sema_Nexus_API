package br.com.nexusbuilderlab.semanexus.core.equipe;

import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.EquipeDto;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.MembroDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de equipes e membros (cadastro e edição são P1, Issue #24). */
@Service
public class EquipeService {

    private final EquipeRepository equipes;

    public EquipeService(EquipeRepository equipes) {
        this.equipes = equipes;
    }

    /**
     * Lista as equipes por nome. As equipes são poucas (estrutura organizacional), então o filtro é aplicado
     * em memória sobre uma única consulta com fetch join, sem N+1.
     */
    @Transactional(readOnly = true)
    public List<EquipeDto> listar(Long setorId, Boolean ativo) {
        Map<Long, Long> membros = new HashMap<>();
        equipes.contarMembrosPorEquipe().forEach(c -> membros.put(c.getEquipeId(), c.getTotal()));
        return equipes.findAllComSetorEResponsavel().stream()
                .filter(e -> setorId == null || e.getSetor().getId().equals(setorId))
                .filter(e -> ativo == null || e.isAtivo() == ativo)
                .map(e -> EquipeDto.de(e, membros.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    /** Membros da equipe por nome; 404 se a equipe não existir. */
    @Transactional(readOnly = true)
    public List<MembroDto> membros(Long equipeId) {
        if (!equipes.existsById(equipeId)) {
            throw RecursoNaoEncontradoException.de("Equipe", equipeId);
        }
        return equipes.findMembros(equipeId).stream().map(MembroDto::de).toList();
    }
}
