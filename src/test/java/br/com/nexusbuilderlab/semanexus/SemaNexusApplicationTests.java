package br.com.nexusbuilderlab.semanexus;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sobe o contexto web (profile dev) e verifica os endpoints públicos e a proteção padrão.
 *
 * <p>O profile {@code openapi} é somado só para desligar o banco: testes unitários não dependem de
 * Oracle. O contexto completo com Oracle real é coberto por {@code CoreSchemaOracleTest} (tag oracle).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "openapi"})
class SemaNexusApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void healthRespondeUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void somenteHealthEstaExpostoNoActuator() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
        mvc.perform(get("/actuator/beans")).andExpect(status().is4xxClientError());
    }

    @Test
    void contratoOpenApiEPublico() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SEMA ALERTA API")))
                .andExpect(content().string(containsString("bearerAuth")));
    }

    @Test
    void swaggerUiEPublico() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void demaisRotasExigemAutenticacao() throws Exception {
        mvc.perform(get("/api/demandas"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void contratoOpenApiDescreveEndpointsDeAutenticacao() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/auth/config'].get").exists());
    }
}
