package br.com.nexusbuilderlab.semanexus.demo;

import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.setor.SetorRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga inicial demonstrativa (setores, usuários e equipes fictícios), idempotente por chave natural:
 * sigla do setor, e-mail do usuário e (setor, nome) da equipe. Só cria o que falta; nunca altera
 * registros existentes. Senhas vêm do recurso já como hash BCrypt — o texto puro não existe em
 * {@code src/main}.
 *
 * <p>Carregado somente nos profiles {@code demo} e {@code dev}, e nunca com {@code openapi} (que roda sem banco).
 */
@Service
@Profile(DemoSeedService.PROFILES)
public class DemoSeedService {

    /** Expressão de profile compartilhada com {@link DemoSeedRunner}. */
    static final String PROFILES = "(demo | dev) & !openapi";
    static final String RECURSO = "demo/usuarios.json";

    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

    private final SetorRepository setores;
    private final UsuarioRepository usuarios;
    private final EquipeRepository equipes;
    private final ObjectMapper mapper;

    public DemoSeedService(SetorRepository setores, UsuarioRepository usuarios, EquipeRepository equipes,
            ObjectMapper mapper) {
        this.setores = setores;
        this.usuarios = usuarios;
        this.equipes = equipes;
        this.mapper = mapper;
    }

    @Transactional
    public ResultadoSeed aplicar() {
        DemoSeedDados dados = carregar();

        int novosSetores = 0;
        Map<String, Setor> setorPorSigla = new HashMap<>();
        for (var s : dados.setores()) {
            Setor setor = setores.findBySigla(s.sigla()).orElse(null);
            if (setor == null) {
                setor = setores.save(new Setor(s.sigla(), s.nome()));
                novosSetores++;
            }
            setorPorSigla.put(s.sigla(), setor);
        }

        int novosUsuarios = 0;
        Map<String, Usuario> usuarioPorEmail = new HashMap<>();
        for (var u : dados.usuarios()) {
            String email = Usuario.normalizarEmail(u.email());
            Usuario usuario = usuarios.findByEmail(email).orElse(null);
            if (usuario == null) {
                validarHash(u);
                usuario = new Usuario(u.nome(), email, u.senhaHash(), Perfil.valueOf(u.perfil()));
                usuario.setSetor(buscar(setorPorSigla, u.setor(), "setor"));
                usuario.setDemo(true);
                usuario = usuarios.save(usuario);
                novosUsuarios++;
            }
            usuarioPorEmail.put(email, usuario);
        }

        int novasEquipes = 0;
        int novosMembros = 0;
        for (var e : dados.equipes()) {
            Setor setor = buscar(setorPorSigla, e.setor(), "setor");
            Equipe equipe = equipes.findBySetorIdAndNome(setor.getId(), e.nome()).orElse(null);
            if (equipe == null) {
                equipe = new Equipe(e.nome(), setor);
                equipe.setResponsavel(buscar(usuarioPorEmail, Usuario.normalizarEmail(e.responsavel()), "usuário"));
                novasEquipes++;
            }
            for (String email : e.membros()) {
                Usuario membro = buscar(usuarioPorEmail, Usuario.normalizarEmail(email), "usuário");
                if (equipe.getMembros().stream().noneMatch(m -> m.getEmail().equals(membro.getEmail()))) {
                    equipe.getMembros().add(membro);
                    novosMembros++;
                }
            }
            equipes.save(equipe);
        }

        return new ResultadoSeed(novosSetores, novosUsuarios, novasEquipes, novosMembros);
    }

    private DemoSeedDados carregar() {
        try (InputStream in = new ClassPathResource(RECURSO).getInputStream()) {
            return mapper.readValue(in, DemoSeedDados.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Não foi possível ler o recurso do seed demonstrativo: " + RECURSO, ex);
        }
    }

    private static void validarHash(DemoSeedDados.UsuarioSeed u) {
        if (u.senhaHash() == null || !BCRYPT.matcher(u.senhaHash()).matches()) {
            throw new IllegalStateException("Seed demonstrativo: a senha de " + u.email() + " não é um hash BCrypt.");
        }
    }

    private static <T> T buscar(Map<String, T> mapa, String chave, String tipo) {
        T valor = mapa.get(chave);
        if (valor == null) {
            throw new IllegalStateException("Seed demonstrativo: " + tipo + " referenciado não existe: " + chave);
        }
        return valor;
    }
}
