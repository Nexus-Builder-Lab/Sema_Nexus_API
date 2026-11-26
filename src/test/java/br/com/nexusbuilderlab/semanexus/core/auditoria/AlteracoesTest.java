package br.com.nexusbuilderlab.semanexus.core.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlteracoesTest {

    @Test
    void guardaSoOsCamposAlteradosNaOrdem() {
        Alteracoes a = Alteracoes.nova()
                .campo("nome", "A", "B")
                .campo("sigla", "X", "X")
                .campo("ativo", true, false)
                .campo("lat", new BigDecimal("-15.50"), new BigDecimal("-15.5"))
                .campo("setorId", null, 3L);

        assertThat(a.antes()).containsOnlyKeys("nome", "ativo", "setorId");
        assertThat(a.antes()).containsEntry("nome", "A").containsEntry("ativo", "true").containsEntry("setorId", null);
        assertThat(a.depois()).containsEntry("setorId", "3").containsEntry("ativo", "false");
        assertThat(a.antesJson()).isEqualTo("{\"nome\":\"A\",\"ativo\":\"true\",\"setorId\":null}");
    }

    @Test
    void entreComparaRetratos() {
        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("titulo", "T1");
        antes.put("prioridade", "ALTA");
        Map<String, Object> depois = new LinkedHashMap<>(antes);
        depois.put("titulo", "T2");

        Alteracoes a = Alteracoes.entre(antes, depois);
        assertThat(a.vazia()).isFalse();
        assertThat(a.antes()).containsOnlyKeys("titulo");
        assertThat(Alteracoes.entre(antes, antes).vazia()).isTrue();
        assertThat(Alteracoes.entre(antes, antes).antesJson()).isNull();
    }

    @Test
    void valoresLongosSaoEncurtadosEJsonCabeNaColuna() {
        Alteracoes a = Alteracoes.nova();
        for (int i = 0; i < 40; i++) {
            a.campo("campo" + i, "ç".repeat(500), "é".repeat(500));
        }
        assertThat(a.antes().get("campo0")).hasSize(Alteracoes.LIMITE_VALOR);
        assertThat(a.antesJson().getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(Alteracoes.LIMITE_BYTES);
        assertThat(a.depoisJson()).startsWith("{");
    }
}
