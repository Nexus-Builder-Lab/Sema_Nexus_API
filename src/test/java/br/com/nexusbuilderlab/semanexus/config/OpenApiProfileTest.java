package br.com.nexusbuilderlab.semanexus.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** O profile openapi precisa subir sem banco: é o que o CI usa para exportar o contrato. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("openapi")
class OpenApiProfileTest {

    @Autowired
    MockMvc mvc;

    @Test
    void exportaContratoSemBancoDeDados() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("SEMA ALERTA API"));
    }
}
