package br.com.nexusbuilderlab.semanexus.flux.demanda;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NumeracaoDemandaRepository extends JpaRepository<NumeracaoDemanda, Integer> {

    /** {@code SELECT ... FOR UPDATE}: serializa a geração de números do mesmo ano até o fim da transação. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NumeracaoDemanda n where n.ano = :ano")
    Optional<NumeracaoDemanda> buscarComLock(@Param("ano") Integer ano);
}
