package br.com.nexusbuilderlab.semanexus.core.equipe;

import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipeRepository extends JpaRepository<Equipe, Long> {

    List<Equipe> findBySetorIdOrderByNomeAsc(Long setorId);

    Optional<Equipe> findBySetorIdAndNome(Long setorId, String nome);

    /** Todas as equipes com setor e responsável já carregados (evita N+1), por nome. */
    @Query("select e from Equipe e join fetch e.setor left join fetch e.responsavel order by e.nome")
    List<Equipe> findAllComSetorEResponsavel();

    /** Membros de uma equipe, por nome. */
    @Query("select u from Equipe e join e.membros u where e.id = :equipeId order by u.nome")
    List<Usuario> findMembros(@Param("equipeId") Long equipeId);

    /** IDs das equipes das quais o usuário é membro (escopo do perfil FISCAL nas demandas). */
    @Query("select e.id from Equipe e join e.membros m where m.id = :usuarioId")
    List<Long> findIdsByMembro(@Param("usuarioId") Long usuarioId);

    /** Quantidade de membros por equipe (equipes sem membros aparecem com 0). */
    @Query("select e.id as equipeId, count(m) as total from Equipe e left join e.membros m group by e.id")
    List<ContagemMembros> contarMembrosPorEquipe();

    interface ContagemMembros {
        Long getEquipeId();

        Long getTotal();
    }
}
