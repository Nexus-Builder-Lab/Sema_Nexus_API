package br.com.nexusbuilderlab.semanexus.security;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tokens revogados (consulta pela chave primária {@code JTI}). */
public interface TokenRevogadoRepository extends JpaRepository<TokenRevogado, String> {

    /**
     * Grava o token revogado só se ainda não existir (MERGE do Oracle). Devolve 1 quando inseriu e 0 quando já estava
     * revogado. Duas transações simultâneas com o mesmo {@code jti} ainda podem esbarrar na PK (ORA-00001) — quem chama
     * trata isso como "já revogado".
     */
    @Modifying
    @Query(value = "MERGE INTO TOKEN_REVOGADO t USING (SELECT :jti AS JTI FROM DUAL) s ON (t.JTI = s.JTI) "
            + "WHEN NOT MATCHED THEN INSERT (JTI, USUARIO_ID, EXPIRA_EM, REVOGADO_EM) "
            + "VALUES (:jti, :usuarioId, :expiraEm, :revogadoEm)", nativeQuery = true)
    int inserirSeAusente(@Param("jti") String jti, @Param("usuarioId") Long usuarioId,
                         @Param("expiraEm") LocalDateTime expiraEm, @Param("revogadoEm") LocalDateTime revogadoEm);

    /** Remove os registros de tokens que já expiraram (não precisam mais ser bloqueados). */
    @Modifying
    @Query("delete from TokenRevogado t where t.expiraEm < :agora")
    int removerExpirados(@Param("agora") LocalDateTime agora);
}
