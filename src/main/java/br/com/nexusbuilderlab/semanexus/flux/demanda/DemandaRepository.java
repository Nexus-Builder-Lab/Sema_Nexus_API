package br.com.nexusbuilderlab.semanexus.flux.demanda;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso às demandas (a exclusão lógica é aplicada pela própria entidade). */
public interface DemandaRepository extends JpaRepository<Demanda, Long>, JpaSpecificationExecutor<Demanda> {

    /** Demanda com as associações exibidas no detalhe já carregadas (evita N+1). */
    @Query("""
            select d from Demanda d
              join fetch d.municipio
              join fetch d.criadoPor
              left join fetch d.equipe
              left join fetch d.fiscal
            where d.id = :id""")
    Optional<Demanda> buscarDetalhe(@Param("id") Long id);

    /** Lock pessimista para editar/excluir (sem fetch join: o Oracle não aceita FOR UPDATE com outer join). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Demanda d where d.id = :id")
    Optional<Demanda> buscarParaEscrita(@Param("id") Long id);
}
