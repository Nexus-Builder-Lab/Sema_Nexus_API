package br.com.nexusbuilderlab.semanexus.flux.demanda;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
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

    /** Demandas com fiscal e prazo no intervalo [de, ate], ainda não finalizadas (usadas pelas notificações). */
    @Query("""
            select d from Demanda d
            where d.fiscal is not null and d.prazo between :de and :ate and d.status <> br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda.FINALIZADA""")
    List<Demanda> buscarComPrazoEntre(@Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** Demandas com fiscal, prazo vencido (anterior a {@code hoje}) e não finalizadas. */
    @Query("""
            select d from Demanda d
            where d.fiscal is not null and d.prazo < :hoje and d.status <> br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda.FINALIZADA""")
    List<Demanda> buscarAtrasadas(@Param("hoje") LocalDate hoje);
}
