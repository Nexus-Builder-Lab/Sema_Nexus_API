package br.com.nexusbuilderlab.semanexus.docia.documento;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentoRepository extends JpaRepository<Documento, Long>, JpaSpecificationExecutor<Documento> {

    @Query("select d from Documento d where d.id = :id and d.excluidoEm is null")
    Optional<Documento> buscarAtivo(@Param("id") Long id);

    @Query("select count(d) from Documento d where d.excluidoEm is null")
    long contarAtivos();
}
