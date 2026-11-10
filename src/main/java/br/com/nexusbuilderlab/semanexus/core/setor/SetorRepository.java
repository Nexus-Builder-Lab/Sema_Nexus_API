package br.com.nexusbuilderlab.semanexus.core.setor;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SetorRepository extends JpaRepository<Setor, Long> {

    Optional<Setor> findBySigla(String sigla);
}
