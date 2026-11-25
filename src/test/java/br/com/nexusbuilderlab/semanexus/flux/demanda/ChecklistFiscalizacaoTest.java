package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.GlobalExceptionHandler.ErroCampo;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ChecklistModelo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ChecklistPreenchido;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChecklistFiscalizacaoTest {

    static final CategoriaFiscalizacao DESMATE = CategoriaFiscalizacao.DESMATAMENTO_ILEGAL;

    private static Map<String, Object> valido() {
        Map<String, Object> m = new HashMap<>();
        m.put("local_acessivel", true);
        m.put("supressao_confirmada", true);
        m.put("area_estimada_ha", 12.5);
        m.put("maquinario_no_local", false);
        return m;
    }

    private static List<String> campos(Map<String, Object> respostas, CategoriaFiscalizacao c) {
        try {
            ChecklistFiscalizacao.validarEmJson(c, respostas);
        } catch (DadosInvalidosException e) {
            return e.getErros().stream().map(ErroCampo::campo).toList();
        }
        return List.of();
    }

    @Test
    void todaCategoriaTemModeloComIdsUnicos() {
        assertThat(ChecklistFiscalizacao.modelos()).hasSize(CategoriaFiscalizacao.values().length);
        for (ChecklistModelo m : ChecklistFiscalizacao.modelos()) {
            assertThat(m.versao()).isEqualTo(ChecklistFiscalizacao.VERSAO);
            assertThat(m.itens().stream().map(ChecklistModelo.Item::id).distinct().count()).isEqualTo(m.itens().size());
            assertThat(m.itens()).anyMatch(ChecklistModelo.Item::obrigatorio);
        }
    }

    @Test
    void checklistValidoViraJsonVersionadoELegivel() {
        Map<String, Object> respostas = valido();
        respostas.put("observacoes_checklist", "  Área fictícia de teste  ");
        String json = ChecklistFiscalizacao.validarEmJson(DESMATE, respostas);
        ChecklistPreenchido lido = ChecklistFiscalizacao.ler(json);
        assertThat(lido.versao()).isEqualTo(1);
        assertThat(lido.categoria()).isEqualTo(DESMATE);
        assertThat(lido.itens()).containsEntry("supressao_confirmada", true)
                .containsEntry("observacoes_checklist", "Área fictícia de teste");
        assertThat(new BigDecimal(lido.itens().get("area_estimada_ha").toString())).isEqualByComparingTo("12.5");
    }

    @Test
    void semChecklistNadaEhGravado() {
        assertThat(ChecklistFiscalizacao.validarEmJson(DESMATE, null)).isNull();
        assertThat(ChecklistFiscalizacao.ler(null)).isNull();
        assertThat(ChecklistFiscalizacao.ler("não é json")).isNull();
    }

    @Test
    void itemDesconhecidoEhRecusado() {
        Map<String, Object> r = valido();
        r.put("item_inventado", true);
        assertThat(campos(r, DESMATE)).containsExactly("checklist.item_inventado");
        // Item válido de outra categoria também é desconhecido aqui.
        Map<String, Object> outro = valido();
        outro.put("foco_ativo", true);
        assertThat(campos(outro, DESMATE)).containsExactly("checklist.foco_ativo");
    }

    @Test
    void obrigatorioAusenteOuVazioEhRecusado() {
        Map<String, Object> r = valido();
        r.remove("maquinario_no_local");
        r.put("area_estimada_ha", null);
        assertThat(campos(r, DESMATE)).containsExactlyInAnyOrder("checklist.maquinario_no_local",
                "checklist.area_estimada_ha");
        Map<String, Object> hidrico = new HashMap<>(Map.of("local_acessivel", true, "captacao_irregular", false,
                "lancamento_efluente", false, "corpo_hidrico", "   "));
        assertThat(campos(hidrico, CategoriaFiscalizacao.RECURSOS_HIDRICOS)).containsExactly("checklist.corpo_hidrico");
    }

    @Test
    void tipoErradoEhRecusado() {
        Map<String, Object> r = valido();
        r.put("local_acessivel", "sim");
        r.put("area_estimada_ha", "12");
        r.put("observacoes_checklist", 5);
        assertThat(campos(r, DESMATE)).containsExactlyInAnyOrder("checklist.local_acessivel",
                "checklist.area_estimada_ha", "checklist.observacoes_checklist");
        Map<String, Object> negativo = valido();
        negativo.put("area_estimada_ha", -1);
        assertThat(campos(negativo, DESMATE)).containsExactly("checklist.area_estimada_ha");
        Map<String, Object> longo = valido();
        longo.put("observacoes_checklist", "x".repeat(ChecklistFiscalizacao.LIMITE_TEXTO + 1));
        assertThat(campos(longo, DESMATE)).containsExactly("checklist.observacoes_checklist");
    }

    @Test
    void chaveMaliciosaNaoVoltaCrua() {
        Map<String, Object> r = valido();
        r.put("<script>x</script>", true);
        assertThat(campos(r, DESMATE)).containsExactly("checklist.scriptxscript");
    }

    @Test
    void demandaSemCategoriaNaoAceitaChecklist() {
        assertThatThrownBy(() -> ChecklistFiscalizacao.validarEmJson(null, valido()))
                .isInstanceOf(DadosInvalidosException.class);
    }
}
