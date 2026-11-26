package br.com.nexusbuilderlab.semanexus.flux.demanda;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FiscalizacaoFotoRepository extends JpaRepository<FiscalizacaoFoto, Long> {

    /** Fotos da demanda (mais antigas primeiro) com o autor carregado. */
    @Query("""
            select f from FiscalizacaoFoto f
              join fetch f.autor
            where f.demanda.id = :demandaId
            order by f.criadoEm, f.id""")
    List<FiscalizacaoFoto> listarPorDemanda(@Param("demandaId") Long demandaId);

    /** Foto da demanda informada (vazio se a foto for de outra demanda). */
    @Query("""
            select f from FiscalizacaoFoto f
              join fetch f.autor
            where f.id = :fotoId and f.demanda.id = :demandaId""")
    Optional<FiscalizacaoFoto> buscarDaDemanda(@Param("demandaId") Long demandaId, @Param("fotoId") Long fotoId);

    long countByDemandaId(Long demandaId);
}
