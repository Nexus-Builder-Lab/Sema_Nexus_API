package br.com.nexusbuilderlab.semanexus.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CatalogoController;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CatalogoService;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Revisão de segurança da API (Issue #19): checklist automatizado. Cobre {@code @PreAuthorize} em todos os
 * endpoints, cabeçalhos de segurança, actuator só com health, erros sem stacktrace e ausência de segredos em logs.
 */
@WebMvcTest(controllers = CatalogoController.class)
@Import({SecurityConfig.class, RevisaoSegurancaApiTest.Config.class})
class RevisaoSegurancaApiTest {

    private static final String BASE = "br.com.nexusbuilderlab.semanexus";

    /** Controllers com endpoints públicos (login/config) ou próprios do usuário autenticado. */
    private static final Set<String> SO_AUTENTICADO = Set.of("AuthController");

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService("segredo-de-teste-com-mais-de-32-caracteres!!", Duration.ofHours(1), Clock.systemUTC());
        }
    }

    @Autowired
    MockMvc mvc;
    @MockitoBean
    UsuarioRepository usuarios;
    @MockitoBean
    CatalogoService catalogos;

    @Test
    void todoEndpointTemPreAuthorizeOuEControllerSoDeAutenticado() throws Exception {
        List<String> semProtecao = new ArrayList<>();
        for (Class<?> c : controllers()) {
            if (SO_AUTENTICADO.contains(c.getSimpleName())) {
                continue;
            }
            boolean classe = c.isAnnotationPresent(PreAuthorize.class);
            for (Method m : c.getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class)
                        && !classe && !m.isAnnotationPresent(PreAuthorize.class)) {
                    semProtecao.add(c.getSimpleName() + "." + m.getName());
                }
            }
        }
        assertThat(semProtecao).as("endpoints sem @PreAuthorize").isEmpty();
    }

    @Test
    void respostaTrazCabecalhosDeSegurancaMesmoNoErro401() throws Exception {
        mvc.perform(get("/api/catalogos/municipios"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void actuatorSoExpoeHealthEErrosNaoVazamStacktrace() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var p = yaml.getObject();
        assertThat(p.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(p.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(p.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(p.getProperty("server.error.include-exception")).isEqualTo("false");
    }

    @Test
    void actuatorNaoPublicoAlemDoHealth() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/heapdump")).andExpect(status().isUnauthorized());
    }

    @Test
    void semCorsForaDoProfileDev() throws Exception {
        mvc.perform(get("/api/catalogos/municipios").header("Origin", "http://evil.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void logsNaoRegistramSenhaTokenOuSegredo() throws IOException {
        Pattern log = Pattern.compile("\\blog(ger)?\\.(trace|debug|info|warn|error)\\(.*");
        Pattern sensivel = Pattern.compile("(?i)(senha|password|token|authorization|bearer|\\bsecret\\b|hash)");
        List<String> achados = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(Path.of("src/main/java"))) {
            for (Path f : arquivos.filter(a -> a.toString().endsWith(".java")).toList()) {
                for (String linha : Files.readAllLines(f)) {
                    if (log.matcher(linha).find() && sensivel.matcher(linha).find()
                            && !linha.contains("não definido")) {
                        achados.add(f.getFileName() + ": " + linha.trim());
                    }
                }
            }
        }
        assertThat(achados).as("logs com termos sensíveis").isEmpty();
    }

    private static List<Class<?>> controllers() throws Exception {
        var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(
                org.springframework.web.bind.annotation.RestController.class));
        List<Class<?>> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents(BASE)) {
            Class<?> k = Class.forName(bd.getBeanClassName());
            if (k.getProtectionDomain().getCodeSource().getLocation().getPath().contains("/classes")
                    && !k.getProtectionDomain().getCodeSource().getLocation().getPath().contains("test-classes")) {
                out.add(k);
            }
        }
        assertThat(out).isNotEmpty();
        return out;
    }
}
