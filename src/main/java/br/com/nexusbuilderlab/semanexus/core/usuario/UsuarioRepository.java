package br.com.nexusbuilderlab.semanexus.core.usuario;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Recebe o e-mail já normalizado ({@link Usuario#normalizarEmail(String)}). */
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
