package br.com.nexusbuilderlab.semanexus.flux.demanda;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso às chaves de idempotência da sincronização em campo (Issue #147). */
public interface ChaveIdempotenciaRepository extends JpaRepository<ChaveIdempotencia, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from ChaveIdempotencia c
            where c.usuarioId = :usuarioId
              and c.operacao = :operacao
              and c.demandaId = :demandaId
              and c.chave = :chave""")
    Optional<ChaveIdempotencia> buscarParaEscrita(@Param("usuarioId") Long usuarioId,
                                                  @Param("operacao") OperacaoIdempotente operacao,
                                                  @Param("demandaId") Long demandaId,
                                                  @Param("chave") String chave);

    @Modifying
    @Query("delete from ChaveIdempotencia c where c.expiraEm < :agora")
    int removerExpiradas(@Param("agora") LocalDateTime agora);
}
