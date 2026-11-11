package br.com.nexusbuilderlab.semanexus.flux.demanda;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DemandaHistoricoRepository extends JpaRepository<DemandaHistorico, Long> {

    /** Linha do tempo da demanda, do evento mais recente ao mais antigo. */
    @Query("""
            select h from DemandaHistorico h
              join fetch h.usuario
            where h.demanda.id = :demandaId
            order by h.data desc, h.id desc""")
    List<DemandaHistorico> findLinhaDoTempo(@Param("demandaId") Long demandaId);
}
