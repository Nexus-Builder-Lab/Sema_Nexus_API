package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.ArquivoNaoSuportadoException;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FotoFiscalizacao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FotoFiscalizacao.ConferenciaExif;
import br.com.nexusbuilderlab.semanexus.security.JwtService;
import br.com.nexusbuilderlab.semanexus.security.SecurityConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** Fatia web da Issue #145: perfis, escopo (403), 415 por magic bytes e download seguro. */
@WebMvcTest(controllers = FotoFiscalizacaoController.class)
@Import({SecurityConfig.class, FotoFiscalizacaoWebMvcTest.Config.class})
class FotoFiscalizacaoWebMvcTest {

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
    FotoFiscalizacaoService service;

    private String bearer(Perfil perfil) {
        Usuario u = new Usuario("Usuário Teste", perfil.name().toLowerCase() + "@exemplo.test", "hash", perfil);
        long id = 100L + perfil.ordinal();
        ReflectionTestUtils.setField(u, "id", id);
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        return "Bearer " + jwtService.gerar(u);
    }

    private static MockMultipartFile foto() {
        return new MockMultipartFile("arquivo", "vistoria.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
    }

    private static FotoFiscalizacao dto() {
        return new FotoFiscalizacao(7L, 42L, "vistoria.jpg", "image/jpeg", 4, "ab".repeat(32), null, null, null, null,
                false, ConferenciaExif.SEM_GPS, null, LocalDateTime.now());
    }

    @Test
    void checklistsTrazemUmModeloPorCategoria() throws Exception {
        mvc.perform(get("/api/demandas/checklists").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.CONSULTA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(CategoriaFiscalizacao.values().length)))
                .andExpect(jsonPath("$[0].versao").value(ChecklistFiscalizacao.VERSAO))
                .andExpect(jsonPath("$[0].itens[0].id").value("local_acessivel"));
        mvc.perform(get("/api/demandas/checklists")).andExpect(status().isUnauthorized());
    }

    @Test
    void envioSoParaFiscalGestorAdmin() throws Exception {
        when(service.enviar(any(), eq(42L), any())).thenReturn(dto());
        for (Perfil p : List.of(Perfil.FISCAL, Perfil.GESTOR, Perfil.ADMIN)) {
            mvc.perform(multipart("/api/demandas/42/fotos").file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(p)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.endsWith("/api/demandas/42/fotos/7")))
                    .andExpect(jsonPath("$.conferencia").value("SEM_GPS"));
        }
        for (Perfil p : List.of(Perfil.ANALISTA, Perfil.CONSULTA)) {
            mvc.perform(multipart("/api/demandas/42/fotos").file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(p)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(multipart("/api/demandas/42/fotos").file(foto())).andExpect(status().isUnauthorized());
    }

    @Test
    void fiscalForaDoEscopoRecebe403EConteudoFalso415() throws Exception {
        when(service.enviar(any(), eq(43L), any()))
                .thenThrow(new AccessDeniedException("Somente o fiscal designado para a demanda pode executar esta ação."));
        mvc.perform(multipart("/api/demandas/43/fotos").file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(Perfil.FISCAL)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        when(service.enviar(any(), eq(44L), any())).thenThrow(new ArquivoNaoSuportadoException("Conteúdo não reconhecido."));
        mvc.perform(multipart("/api/demandas/44/fotos").file(foto()).header(HttpHeaders.AUTHORIZATION, bearer(Perfil.FISCAL)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        when(service.listar(any(), eq(43L))).thenThrow(new AccessDeniedException("Demanda fora do escopo do fiscal."));
        mvc.perform(get("/api/demandas/43/fotos").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.FISCAL)))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadComTipoDaImagemENosniff() throws Exception {
        when(service.baixar(any(), eq(42L), eq(7L))).thenReturn(
                new FotoFiscalizacaoService.FotoBaixada("vistoria.jpg", "image/jpeg", new byte[] {1, 2, 3}));
        mvc.perform(get("/api/demandas/42/fotos/7/arquivo").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.CONSULTA)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.startsWith("attachment")));
    }

    @Test
    void exclusaoPorOutroUsuarioRecebe403() throws Exception {
        doThrow(new AccessDeniedException("Somente quem enviou a foto pode excluí-la."))
                .when(service).excluir(any(), eq(42L), eq(8L));
        mvc.perform(delete("/api/demandas/42/fotos/8").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.GESTOR)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/demandas/42/fotos/7").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.FISCAL)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/demandas/42/fotos/7").header(HttpHeaders.AUTHORIZATION, bearer(Perfil.CONSULTA)))
                .andExpect(status().isForbidden());
    }
}
