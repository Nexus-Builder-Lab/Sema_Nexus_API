package br.com.nexusbuilderlab.semanexus.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.setor.SetorRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Idempotência do seed com repositórios em memória (sem Oracle). */
class DemoSeedServiceTest {

    private final Map<String, Setor> setores = new HashMap<>();
    private final Map<String, Usuario> usuarios = new HashMap<>();
    private final Map<String, Equipe> equipes = new HashMap<>();

    private SetorRepository setorRepo;
    private UsuarioRepository usuarioRepo;
    private EquipeRepository equipeRepo;
    private DemoSeedService seed;

    @BeforeEach
    void preparar() {
        setorRepo = mock(SetorRepository.class);
        usuarioRepo = mock(UsuarioRepository.class);
        equipeRepo = mock(EquipeRepository.class);

        when(setorRepo.findBySigla(anyString())).thenAnswer(i -> Optional.ofNullable(setores.get(i.<String>getArgument(0))));
        when(setorRepo.save(any(Setor.class))).thenAnswer(i -> {
            Setor s = i.getArgument(0);
            setores.put(s.getSigla(), s);
            return s;
        });
        when(usuarioRepo.findByEmail(anyString())).thenAnswer(i -> Optional.ofNullable(usuarios.get(i.<String>getArgument(0))));
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            usuarios.put(u.getEmail(), u);
            return u;
        });
        when(equipeRepo.findBySetorIdAndNome(any(), anyString()))
                .thenAnswer(i -> Optional.ofNullable(equipes.get(i.<String>getArgument(1))));
        when(equipeRepo.save(any(Equipe.class))).thenAnswer(i -> {
            Equipe e = i.getArgument(0);
            equipes.put(e.getNome(), e);
            return e;
        });

        seed = new DemoSeedService(setorRepo, usuarioRepo, equipeRepo, new ObjectMapper());
    }

    @Test
    void primeiraExecucaoCriaSetoresUsuariosEEquipes() {
        ResultadoSeed r = seed.aplicar();

        assertThat(r.setores()).isEqualTo(3);
        assertThat(r.usuarios()).isEqualTo(6);
        assertThat(r.equipes()).isEqualTo(4);
        assertThat(r.membros()).isEqualTo(6);
        assertThat(r.vazio()).isFalse();
        assertThat(usuarios.keySet()).contains("admin@admin.com");
    }

    @Test
    void usuarioDeTesteEAdminAtivoMarcadoComoDemoComHashBcrypt() {
        seed.aplicar();

        Usuario admin = usuarios.get("admin@admin.com");
        assertThat(admin.getPerfil()).isEqualTo(Perfil.ADMIN);
        assertThat(admin.isAtivo()).isTrue();
        assertThat(admin.isDemo()).isTrue();
        assertThat(admin.getSenhaHash()).startsWith("$2a$");
        assertThat(usuarios.values()).allMatch(Usuario::isDemo);
        assertThat(usuarios.values()).extracting(Usuario::getPerfil)
                .containsExactlyInAnyOrder(Perfil.ADMIN, Perfil.GESTOR, Perfil.FISCAL, Perfil.FISCAL,
                        Perfil.ANALISTA, Perfil.CONSULTA);
        assertThat(usuarios.keySet().stream().filter(e -> !e.equals("admin@admin.com")))
                .allMatch(e -> e.endsWith("@demo.semanexus.local"));
    }

    @Test
    void segundaExecucaoNaoDuplicaNada() {
        seed.aplicar();
        int s = setores.size();
        int u = usuarios.size();
        int e = equipes.size();
        List<Integer> membros = new ArrayList<>();
        equipes.values().forEach(eq -> membros.add(eq.getMembros().size()));

        ResultadoSeed segunda = seed.aplicar();

        assertThat(segunda.vazio()).isTrue();
        assertThat(setores).hasSize(s);
        assertThat(usuarios).hasSize(u);
        assertThat(equipes).hasSize(e);
        List<Integer> depois = new ArrayList<>();
        equipes.values().forEach(eq -> depois.add(eq.getMembros().size()));
        assertThat(depois).containsExactlyInAnyOrderElementsOf(membros);
    }

    @Test
    void usuarioExistenteNaoETocado() {
        Usuario existente = new Usuario("Outro Nome", "admin@admin.com", "$2a$10$hash-ja-existente", Perfil.ADMIN);
        usuarios.put("admin@admin.com", existente);

        ResultadoSeed r = seed.aplicar();

        assertThat(r.usuarios()).isEqualTo(5);
        assertThat(usuarios.get("admin@admin.com")).isSameAs(existente);
        assertThat(existente.getNome()).isEqualTo("Outro Nome");
        ArgumentCaptor<Usuario> salvos = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepo, org.mockito.Mockito.times(5)).save(salvos.capture());
        assertThat(salvos.getAllValues()).noneMatch(x -> x.getEmail().equals("admin@admin.com"));
    }

    @Test
    void nadaEPersistidoQuandoNaoHaNecessidade() {
        seed.aplicar();
        org.mockito.Mockito.clearInvocations(setorRepo, usuarioRepo);

        seed.aplicar();

        verify(setorRepo, never()).save(any());
        verify(usuarioRepo, never()).save(any());
    }
}
