package br.com.nexusbuilderlab.semanexus.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    record Entrada(@NotBlank String titulo, @Size(min = 5, message = "deve ter ao menos 5 caracteres") String descricao) {}

    @RestController
    static class ControllerDeTeste {
        @PostMapping("/teste/validacao")
        Entrada validar(@Valid @RequestBody Entrada entrada) {
            return entrada;
        }

        @GetMapping("/teste/nao-encontrado")
        void naoEncontrado() {
            throw RecursoNaoEncontradoException.de("Demanda", 42);
        }

        @GetMapping("/teste/conflito")
        void conflito() {
            throw new ConflitoException("Transição de Pendente para Finalizada não é permitida.");
        }

        @GetMapping("/teste/erro-interno")
        void erroInterno() {
            throw new IllegalStateException("detalhe interno SECRETO que não pode vazar");
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ControllerDeTeste())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validacaoRetorna400ComListaDeErrosPorCampo() throws Exception {
        mvc.perform(post("/teste/validacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"\",\"descricao\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].campo").value("descricao"))
                .andExpect(jsonPath("$.errors[0].mensagem").value("deve ter ao menos 5 caracteres"))
                .andExpect(jsonPath("$.errors[1].campo").value("titulo"));
    }

    @Test
    void jsonMalformadoRetorna400ProblemDetail() throws Exception {
        mvc.perform(post("/teste/validacao").contentType(MediaType.APPLICATION_JSON).content("{nao é json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void recursoNaoEncontradoRetorna404() throws Exception {
        mvc.perform(get("/teste/nao-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"))
                .andExpect(jsonPath("$.detail").value("Demanda 42 não encontrado(a)."));
    }

    @Test
    void rotaInexistenteRetorna404ProblemDetail() throws Exception {
        mvc.perform(get("/teste/rota-que-nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void conflitoRetorna409() throws Exception {
        mvc.perform(get("/teste/conflito"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Transição de Pendente para Finalizada não é permitida."));
    }

    @Test
    void erroInesperadoRetorna500SemVazarDetalhesNemStacktrace() throws Exception {
        mvc.perform(get("/teste/erro-interno"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(content().string(not(containsString("SECRETO"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at br.com"))));
    }
}
