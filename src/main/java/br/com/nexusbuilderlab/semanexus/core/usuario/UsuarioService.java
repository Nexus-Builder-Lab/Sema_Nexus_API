package br.com.nexusbuilderlab.semanexus.core.usuario;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.Alteracoes;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.setor.SetorRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.dto.UsuarioAtualizacaoRequest;
import br.com.nexusbuilderlab.semanexus.core.usuario.dto.UsuarioCriacaoRequest;
import br.com.nexusbuilderlab.semanexus.core.usuario.dto.UsuarioDto;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão administrativa de usuários: cadastro, edição, ativação e consulta. Regras: senha de 8 a 72 caracteres
 * (hash BCrypt, nunca registrada em auditoria), e-mail único (minúsculas) e proteção do último ADMIN ativo.
 */
@Service
public class UsuarioService {

    static final String ENTIDADE = "USUARIO";
    static final int SENHA_MINIMA = 8;
    static final int SENHA_MAXIMA = 72;
    private static final Sort ORDEM_PADRAO = Sort.by("nome").and(Sort.by("id"));

    private final UsuarioRepository usuarios;
    private final SetorRepository setores;
    private final PasswordEncoder encoder;
    private final AuditService auditoria;

    public UsuarioService(UsuarioRepository usuarios, SetorRepository setores, PasswordEncoder encoder,
                          AuditService auditoria) {
        this.usuarios = usuarios;
        this.setores = setores;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    /** Pesquisa por nome/e-mail ({@code q}, sem diferenciar maiúsculas) com filtros de perfil e situação. */
    @Transactional(readOnly = true)
    public PageResponse<UsuarioDto> listar(String q, Perfil perfil, Boolean ativo, Pageable paginacao) {
        Pageable pagina = PageRequest.of(paginacao.getPageNumber(), paginacao.getPageSize(), ORDEM_PADRAO);
        Page<Usuario> resultado = usuarios.findAll(especificacao(q, perfil, ativo), pagina);
        return PageResponse.of(resultado, UsuarioDto::de);
    }

    @Transactional(readOnly = true)
    public UsuarioDto buscar(Long id) {
        return UsuarioDto.de(obter(id));
    }

    @Transactional
    public UsuarioDto criar(UsuarioCriacaoRequest req) {
        validarSenha(req.senha());
        String email = Usuario.normalizarEmail(req.email());
        if (usuarios.existsByEmail(email)) {
            throw new ConflitoException("Já existe um usuário com este e-mail.");
        }
        Usuario u = new Usuario(req.nome().trim(), email, encoder.encode(req.senha()), req.perfil());
        u.setSetor(setor(req.setorId()));
        usuarios.save(u);
        auditoria.registrar("CRIAR", ENTIDADE, String.valueOf(u.getId()),
                "Usuário criado: perfil " + u.getPerfil() + ", e-mail " + u.getEmail() + ".");
        return UsuarioDto.de(u);
    }

    @Transactional
    public UsuarioDto atualizar(Long id, UsuarioAtualizacaoRequest req) {
        Usuario u = obter(id);
        String email = Usuario.normalizarEmail(req.email());
        usuarios.findByEmail(email).filter(outro -> !outro.getId().equals(id)).ifPresent(outro -> {
            throw new ConflitoException("Já existe um usuário com este e-mail.");
        });
        if (u.getPerfil() == Perfil.ADMIN && u.isAtivo() && req.perfil() != Perfil.ADMIN) {
            exigirOutroAdminAtivo(u);
        }
        Alteracoes alteracoes = Alteracoes.nova();
        String nome = req.nome().trim();
        Long setorAnterior = u.getSetor() == null ? null : u.getSetor().getId();
        alteracoes.campo("nome", u.getNome(), nome).campo("email", u.getEmail(), email)
                .campo("perfil", u.getPerfil(), req.perfil()).campo("setorId", setorAnterior, req.setorId());
        boolean novaSenha = req.senha() != null;
        if (novaSenha) {
            // Nunca o valor: só a indicação de que houve redefinição.
            alteracoes.campo("senha", "(mantida)", "(redefinida)");
            validarSenha(req.senha());
            u.setSenhaHash(encoder.encode(req.senha()));
        }
        u.setNome(nome);
        u.setEmail(email);
        u.setPerfil(req.perfil());
        u.setSetor(setor(req.setorId()));
        usuarios.save(u);
        auditoria.registrarAlteracao("ATUALIZAR", ENTIDADE, String.valueOf(id),
                "Usuário atualizado: perfil " + u.getPerfil() + (novaSenha ? "; senha redefinida." : "."), alteracoes);
        return UsuarioDto.de(u);
    }

    @Transactional
    public UsuarioDto alterarAtivacao(Long id, boolean ativo) {
        Usuario u = obter(id);
        if (!ativo && u.isAtivo() && u.getPerfil() == Perfil.ADMIN) {
            exigirOutroAdminAtivo(u);
        }
        Alteracoes alteracoes = Alteracoes.nova().campo("ativo", u.isAtivo(), ativo);
        u.setAtivo(ativo);
        usuarios.save(u);
        auditoria.registrarAlteracao(ativo ? "ATIVAR" : "DESATIVAR", ENTIDADE, String.valueOf(id),
                ativo ? "Usuário ativado." : "Usuário desativado.", alteracoes);
        return UsuarioDto.de(u);
    }

    private void exigirOutroAdminAtivo(Usuario u) {
        if (usuarios.countByPerfilAndAtivoAndIdNot(Perfil.ADMIN, true, u.getId()) == 0) {
            throw new ConflitoException("Não é possível desativar ou rebaixar o último administrador ativo.");
        }
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMA || senha.length() > SENHA_MAXIMA) {
            throw new DadosInvalidosException("senha", "A senha deve ter entre 8 e 72 caracteres.");
        }
    }

    private Setor setor(Long setorId) {
        if (setorId == null) {
            return null;
        }
        return setores.findById(setorId)
                .orElseThrow(() -> new DadosInvalidosException("setorId", "Setor não encontrado."));
    }

    private Usuario obter(Long id) {
        return usuarios.findById(id).orElseThrow(() -> RecursoNaoEncontradoException.de("Usuário", id));
    }

    private static Specification<Usuario> especificacao(String q, Perfil perfil, Boolean ativo) {
        return (raiz, consulta, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String termo = q.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String like = "%" + termo + "%";
                ps.add(cb.or(cb.like(cb.lower(raiz.get("nome")), like, '\\'),
                        cb.like(cb.lower(raiz.get("email")), like, '\\')));
            }
            if (perfil != null) {
                ps.add(cb.equal(raiz.get("perfil"), perfil));
            }
            if (ativo != null) {
                ps.add(cb.equal(raiz.get("ativo"), ativo));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }
}
