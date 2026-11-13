package br.com.nexusbuilderlab.semanexus.core.notificacao;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    Page<Notificacao> findByUsuarioId(Long usuarioId, Pageable paginacao);

    Page<Notificacao> findByUsuarioIdAndLida(Long usuarioId, boolean lida, Pageable paginacao);

    long countByUsuarioIdAndLidaFalse(Long usuarioId);

    Optional<Notificacao> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByUsuarioIdAndChave(Long usuarioId, String chave);
}
