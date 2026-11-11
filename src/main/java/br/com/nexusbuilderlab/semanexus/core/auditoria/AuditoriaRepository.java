package br.com.nexusbuilderlab.semanexus.core.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long>, JpaSpecificationExecutor<Auditoria> {

    java.util.List<Auditoria> findTop50ByEntidadeAndEntidadeIdOrderByDataDescIdDesc(String entidade, String entidadeId);
}
