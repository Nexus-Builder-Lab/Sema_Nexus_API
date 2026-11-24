package br.com.nexusbuilderlab.semanexus.security;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tokens revogados (consulta pela chave primária {@code JTI}). */
public interface TokenRevogadoRepository extends JpaRepository<TokenRevogado, String> {

    /** Remove os registros de tokens que já expiraram (não precisam mais ser bloqueados). */
    @Modifying
    @Query("delete from TokenRevogado t where t.expiraEm < :agora")
    int removerExpirados(@Param("agora") LocalDateTime agora);
}
