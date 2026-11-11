package br.com.nexusbuilderlab.semanexus.flux.demanda;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FiscalizacaoRepository extends JpaRepository<Fiscalizacao, Long> {

    /** Registro de fiscalização da demanda (1:1) com equipe e responsável carregados. */
    @Query("""
            select f from Fiscalizacao f
              join fetch f.equipe
              join fetch f.registradoPor
            where f.demanda.id = :demandaId""")
    Optional<Fiscalizacao> buscarPorDemanda(@Param("demandaId") Long demandaId);

    boolean existsByDemandaId(Long demandaId);
}
