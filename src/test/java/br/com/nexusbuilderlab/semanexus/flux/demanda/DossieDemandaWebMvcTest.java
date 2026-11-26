package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** Fatia web do dossiê em PDF (Issue #146): autenticação, download application/pdf e 403/404 em problem+json. */
@WebMvcTest(controllers = DossieDemandaController.class)
@Import({SecurityConfig.class, DossieDemandaWebMvcTest.Config.class})
class DossieDemandaWebMvcTest {

    static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres!!";

    @TestConfiguration
    static class Config {
        @Bean
        JwtService jwtService() {
            return new JwtService(SEGREDO, Duration.ofHours(8), Clock.systemUTC());
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwtService;
    @MockitoBean
    UsuarioRepository usuarios;
    @MockitoBean
    DossieDemandaService service;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 200L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    @Test
    void exigeAutenticacao() throws Exception {
        mvc.perform(get("/api/demandas/10/dossie.pdf"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void exportaPdfParaPerfisComAcesso() throws Exception {
        byte[] pdfBytes = "%PDF-1.4\n%%EOF".getBytes();
        when(service.gerar(any(), eq(10L)))
                .thenReturn(new DossieDemandaService.PdfDossie("dossie-DEM-2026-0010.pdf", pdfBytes, "AAAAAA-BBBBBB-CCCCCC-DDDDDD"));

        for (Perfil p : List.of(Perfil.ADMIN, Perfil.GESTOR, Perfil.ANALISTA, Perfil.CONSULTA, Perfil.FISCAL)) {
            mvc.perform(get("/api/demandas/10/dossie.pdf").header(HttpHeaders.AUTHORIZATION, bearer(p)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment")))
                    .andExpect(content().bytes(pdfBytes));
        }
    }

    @Test
    void fiscalForaDoEscopoRecebe403EDemandaInexistente404EmProblemJson() throws Exception {
        when(service.gerar(any(), eq(43L)))
                .thenThrow(new AccessDeniedException("Demanda fora do escopo do fiscal."));
        mvc.perform(get("/api/demandas/43/dossie.pdf").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.FISCAL)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(403));

        when(service.gerar(any(), eq(999L)))
                .thenThrow(RecursoNaoEncontradoException.de("Demanda", 999L));
        mvc.perform(get("/api/demandas/999/dossie.pdf").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.GESTOR)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }
}
