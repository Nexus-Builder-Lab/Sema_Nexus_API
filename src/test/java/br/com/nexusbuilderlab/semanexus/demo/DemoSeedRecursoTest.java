package br.com.nexusbuilderlab.semanexus.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Garante que o recurso do seed guarda só hashes BCrypt corretos e que o texto puro não vai para src/main. */
class DemoSeedRecursoTest {

    // Senhas de demonstração (também documentadas em docs/DEMO.md). Permitidas aqui por serem teste.
    private static final String SENHA_ADMIN = "12345678";
    private static final String SENHA_DEMAIS = "Demo@Sema2026";

    private DemoSeedDados carregar() throws IOException {
        try (InputStream in = new ClassPathResource(DemoSeedService.RECURSO).getInputStream()) {
            return new ObjectMapper().readValue(in, DemoSeedDados.class);
        }
    }

    @Test
    void hashesSaoBcryptEBatemComAsSenhasDeDemonstracao() throws IOException {
        var encoder = new BCryptPasswordEncoder();
        for (var u : carregar().usuarios()) {
            assertThat(u.senhaHash()).matches("^\\$2[ab]\\$\\d{2}\\$.{53}$");
            String esperada = u.email().equals("admin@admin.com") ? SENHA_ADMIN : SENHA_DEMAIS;
            assertThat(encoder.matches(esperada, u.senhaHash())).as("senha de %s", u.email()).isTrue();
        }
    }

    @Test
    void referenciasInternasSaoConsistentes() throws IOException {
        DemoSeedDados d = carregar();
        List<String> siglas = d.setores().stream().map(DemoSeedDados.SetorSeed::sigla).toList();
        List<String> emails = d.usuarios().stream().map(DemoSeedDados.UsuarioSeed::email).toList();

        assertThat(d.usuarios()).extracting(DemoSeedDados.UsuarioSeed::setor).allMatch(siglas::contains);
        assertThat(d.equipes()).hasSize(4);
        for (var e : d.equipes()) {
            assertThat(siglas).contains(e.setor());
            assertThat(emails).contains(e.responsavel());
            assertThat(emails).containsAll(e.membros());
        }
        assertThat(emails).filteredOn(e -> !e.equals("admin@admin.com"))
                .allMatch(e -> e.endsWith("@demo.semanexus.local"));
    }

    @Test
    void textoPuroDasSenhasNaoApareceEmSrcMain() throws IOException {
        Path raiz = Path.of("src", "main");
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            for (Path p : arquivos.filter(Files::isRegularFile).filter(f -> !f.toString().endsWith(".gz")).toList()) {
                String conteudo = Files.readString(p);
                assertThat(conteudo).as(p.toString()).doesNotContain(SENHA_ADMIN).doesNotContain(SENHA_DEMAIS);
            }
        }
    }

    @Test
    void toStringDoUsuarioNaoExpoeHash() throws IOException {
        var u = carregar().usuarios().get(0);

        assertThat(u.toString()).doesNotContain(u.senhaHash()).doesNotContain("$2a$");
    }
}
