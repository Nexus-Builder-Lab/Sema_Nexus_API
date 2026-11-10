package br.com.nexusbuilderlab.semanexus.core.municipio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MunicipioRepository extends JpaRepository<Municipio, Integer> {

    List<Municipio> findAllByOrderByNomeAsc();

    List<Municipio> findByBiomaOrderByNomeAsc(Bioma bioma);
}
