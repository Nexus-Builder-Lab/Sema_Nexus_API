package br.com.nexusbuilderlab.semanexus.core.notificacao;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.notificacao.dto.ContagemNaoLidas;
import br.com.nexusbuilderlab.semanexus.core.notificacao.dto.NotificacaoDto;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notificações por usuário (Issue #25). A geração é idempotente por ({@code usuário}, {@code chave}): repetir
 * a mesma chave não cria nova notificação. A consulta e a leitura são sempre restritas ao dono.
 */
@Service
public class NotificacaoService {

    static final ZoneId FUSO = ZoneId.of("America/Cuiaba");
    /** Perfis que recebem avisos de gestão (nova demanda, atrasos). */
    static final List<Perfil> PERFIS_GESTAO = List.of(Perfil.GESTOR, Perfil.ADMIN);

    private final NotificacaoRepository notificacoes;
    private final UsuarioRepository usuarios;
    private final Clock clock;
    private final ApplicationEventPublisher eventos;

    /** Testes: sem publicação de eventos. */
    public NotificacaoService(NotificacaoRepository notificacoes, UsuarioRepository usuarios, Clock clock) {
        this(notificacoes, usuarios, clock, evento -> { });
    }

    @Autowired
    public NotificacaoService(NotificacaoRepository notificacoes, UsuarioRepository usuarios, Clock clock,
                              ApplicationEventPublisher eventos) {
        this.notificacoes = notificacoes;
        this.usuarios = usuarios;
        this.clock = clock;
        this.eventos = eventos;
    }

    // ------------------------------------------------------------------ geração

    /** Cria a notificação, salvo se o usuário já tiver uma com a mesma chave. Devolve se foi criada. */
    @Transactional
    public boolean notificar(Long usuarioId, TipoNotificacao tipo, String titulo, String mensagem, Long demandaId,
                             String chave) {
        if (usuarioId == null || notificacoes.existsByUsuarioIdAndChave(usuarioId, chave)) {
            return false;
        }
        Notificacao nova = new Notificacao(usuarioId, tipo, limitar(titulo, 200), limitar(mensagem, 1000),
                demandaId, chave, agora());
        notificacoes.save(nova);
        // Entregue em tempo real (SSE) só ao dono e só depois do commit (CanalNotificacoes).
        eventos.publishEvent(new NotificacaoCriada(usuarioId, NotificacaoDto.de(nova)));
        return true;
    }

    /** Notifica todos os gestores ativos (GESTOR e ADMIN), exceto {@code excetoUsuarioId}. Devolve quantas criou. */
    @Transactional
    public int notificarGestores(TipoNotificacao tipo, String titulo, String mensagem, Long demandaId, String chave,
                                 Long excetoUsuarioId) {
        int criadas = 0;
        for (var u : usuarios.findByPerfilInAndAtivoTrue(PERFIS_GESTAO)) {
            if (!u.getId().equals(excetoUsuarioId) && notificar(u.getId(), tipo, titulo, mensagem, demandaId, chave)) {
                criadas++;
            }
        }
        return criadas;
    }

    // ------------------------------------------------------------------ consulta (somente do usuário logado)

    @Transactional(readOnly = true)
    public PageResponse<NotificacaoDto> listar(UsuarioAutenticado ator, Boolean lida, Pageable paginacao) {
        Pageable p = PageRequest.of(paginacao.getPageNumber(), paginacao.getPageSize(),
                Sort.by(Sort.Direction.DESC, "criadaEm").and(Sort.by(Sort.Direction.DESC, "id")));
        var pagina = lida == null ? notificacoes.findByUsuarioId(ator.id(), p)
                : notificacoes.findByUsuarioIdAndLida(ator.id(), lida, p);
        return PageResponse.of(pagina, NotificacaoDto::de);
    }

    @Transactional(readOnly = true)
    public ContagemNaoLidas contarNaoLidas(UsuarioAutenticado ator) {
        return new ContagemNaoLidas(notificacoes.countByUsuarioIdAndLidaFalse(ator.id()));
    }

    /** Marca como lida; 404 se a notificação não existe ou pertence a outro usuário. */
    @Transactional
    public NotificacaoDto marcarLida(UsuarioAutenticado ator, Long id) {
        Notificacao n = notificacoes.findByIdAndUsuarioId(id, ator.id())
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Notificação", id));
        n.marcarLida(agora());
        return NotificacaoDto.de(notificacoes.save(n));
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock.withZone(FUSO));
    }

    private static String limitar(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
