package br.com.nexusbuilderlab.semanexus.core.usuario;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    /** Recebe o e-mail já normalizado ({@link Usuario#normalizarEmail(String)}). */
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Usuários com perfil e situação dados, exceto o id informado (proteção do último ADMIN ativo). */
    long countByPerfilAndAtivoAndIdNot(Perfil perfil, boolean ativo, Long id);
}
