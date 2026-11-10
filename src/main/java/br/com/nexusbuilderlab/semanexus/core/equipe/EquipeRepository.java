package br.com.nexusbuilderlab.semanexus.core.equipe;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipeRepository extends JpaRepository<Equipe, Long> {

    List<Equipe> findBySetorIdOrderByNomeAsc(Long setorId);
}
