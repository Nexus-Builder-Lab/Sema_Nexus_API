package br.com.nexusbuilderlab.semanexus.core.equipe;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.EquipeDto;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.EquipeRequest;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.MembroDto;
import br.com.nexusbuilderlab.semanexus.core.equipe.dto.MembrosRequest;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.setor.SetorRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta e cadastro/edição de equipes, membros e responsável (Issues #7 e #24); alterações são auditadas. */
@Service
public class EquipeService {

    static final String ENTIDADE = "EQUIPE";

    private final EquipeRepository equipes;
    private final SetorRepository setores;
    private final UsuarioRepository usuarios;
    private final AuditService auditoria;

    public EquipeService(EquipeRepository equipes, SetorRepository setores, UsuarioRepository usuarios,
                         AuditService auditoria) {
        this.equipes = equipes;
        this.setores = setores;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    /**
     * Lista as equipes por nome. As equipes são poucas (estrutura organizacional), então o filtro é aplicado
     * em memória sobre uma única consulta com fetch join, sem N+1.
     */
    @Transactional(readOnly = true)
    public List<EquipeDto> listar(Long setorId, Boolean ativo) {
        Map<Long, Long> membros = new HashMap<>();
        equipes.contarMembrosPorEquipe().forEach(c -> membros.put(c.getEquipeId(), c.getTotal()));
        return equipes.findAllComSetorEResponsavel().stream()
                .filter(e -> setorId == null || e.getSetor().getId().equals(setorId))
                .filter(e -> ativo == null || e.isAtivo() == ativo)
                .map(e -> EquipeDto.de(e, membros.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    /** Membros da equipe por nome; 404 se a equipe não existir. */
    @Transactional(readOnly = true)
    public List<MembroDto> membros(Long equipeId) {
        if (!equipes.existsById(equipeId)) {
            throw RecursoNaoEncontradoException.de("Equipe", equipeId);
        }
        return equipes.findMembros(equipeId).stream().map(MembroDto::de).toList();
    }

    /** Cria a equipe; o responsável (se informado) entra como membro. Nome repetido no setor responde 409. */
    @Transactional
    public EquipeDto criar(EquipeRequest req) {
        Setor setor = setor(req.setorId());
        String nome = req.nome().trim();
        exigirNomeLivre(setor, nome, null);
        Equipe equipe = new Equipe(nome, setor);
        equipe.setAtivo(req.ativo() == null || req.ativo());
        definirResponsavel(equipe, req.responsavelId());
        equipes.save(equipe);
        auditoria.registrar("CRIAR", ENTIDADE, String.valueOf(equipe.getId()),
                "Equipe criada: " + nome + " (setor " + setor.getSigla() + ").");
        return EquipeDto.de(equipe, equipe.getMembros().size());
    }

    @Transactional
    public EquipeDto atualizar(Long id, EquipeRequest req) {
        Equipe equipe = obter(id);
        Setor setor = setor(req.setorId());
        String nome = req.nome().trim();
        exigirNomeLivre(setor, nome, id);
        equipe.setNome(nome);
        equipe.setSetor(setor);
        if (req.ativo() != null) {
            equipe.setAtivo(req.ativo());
        }
        definirResponsavel(equipe, req.responsavelId());
        equipes.save(equipe);
        auditoria.registrar("ATUALIZAR", ENTIDADE, String.valueOf(id),
                "Equipe atualizada: " + nome + (equipe.isAtivo() ? " (ativa)." : " (inativa)."));
        return EquipeDto.de(equipe, equipe.getMembros().size());
    }

    /**
     * Substitui o conjunto de membros (somente usuários ativos). O responsável informado entra como membro;
     * sem {@code responsavelId}, o atual é mantido se continuar na equipe e removido caso contrário.
     */
    @Transactional
    public EquipeDto definirMembros(Long id, MembrosRequest req) {
        Equipe equipe = obter(id);
        Set<Long> ids = new LinkedHashSet<>(req.usuarioIds());
        if (req.responsavelId() != null) {
            ids.add(req.responsavelId());
        }
        if (ids.contains(null)) {
            throw new DadosInvalidosException("usuarioIds", "A lista contém um identificador nulo.");
        }
        Set<Usuario> novos = new LinkedHashSet<>();
        for (Long uid : ids) {
            novos.add(usuarioAtivo(uid, "usuarioIds"));
        }
        equipe.getMembros().clear();
        equipe.getMembros().addAll(novos);
        if (req.responsavelId() != null) {
            novos.stream().filter(u -> u.getId().equals(req.responsavelId())).findFirst()
                    .ifPresent(equipe::setResponsavel);
        } else if (equipe.getResponsavel() != null && !ids.contains(equipe.getResponsavel().getId())) {
            equipe.setResponsavel(null);
        }
        equipes.save(equipe);
        auditoria.registrar("ATUALIZAR_MEMBROS", ENTIDADE, String.valueOf(id),
                "Membros da equipe definidos: " + ids.size() + " usuário(s) " + ids + ".");
        return EquipeDto.de(equipe, novos.size());
    }

    private void definirResponsavel(Equipe equipe, Long responsavelId) {
        if (responsavelId == null) {
            equipe.setResponsavel(null);
            return;
        }
        Usuario responsavel = usuarioAtivo(responsavelId, "responsavelId");
        equipe.setResponsavel(responsavel);
        equipe.getMembros().add(responsavel);
    }

    private void exigirNomeLivre(Setor setor, String nome, Long idAtual) {
        equipes.findBySetorIdAndNome(setor.getId(), nome).filter(e -> !e.getId().equals(idAtual)).ifPresent(e -> {
            throw new ConflitoException("Já existe uma equipe com o nome '" + nome + "' neste setor.");
        });
    }

    private Setor setor(Long setorId) {
        return setores.findById(setorId)
                .orElseThrow(() -> new DadosInvalidosException("setorId", "Setor não encontrado."));
    }

    private Usuario usuarioAtivo(Long id, String campo) {
        Usuario u = usuarios.findById(id)
                .orElseThrow(() -> new DadosInvalidosException(campo, "Usuário " + id + " não encontrado."));
        if (!u.isAtivo()) {
            throw new DadosInvalidosException(campo, "Usuário " + id + " está inativo.");
        }
        return u;
    }

    private Equipe obter(Long id) {
        return equipes.findById(id).orElseThrow(() -> RecursoNaoEncontradoException.de("Equipe", id));
    }
}
