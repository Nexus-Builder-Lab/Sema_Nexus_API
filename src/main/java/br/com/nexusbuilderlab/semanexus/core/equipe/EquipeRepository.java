package br.com.nexusbuilderlab.semanexus.core.equipe;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipeRepository extends JpaRepository<Equipe, Long> {

    List<Equipe> findBySetorIdOrderByNomeAsc(Long setorId);

    Optional<Equipe> findBySetorIdAndNome(Long setorId, String nome);
}
