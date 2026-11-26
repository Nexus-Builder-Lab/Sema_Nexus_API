package br.com.nexusbuilderlab.semanexus.flux.demanda;

import java.time.LocalDate;
import java.util.Map;

import br.com.nexusbuilderlab.semanexus.agentes.governanca.GovernancaIaService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DecisaoTriagem;

/**
 * Campos comparados na governança de IA (Issue #131), montados do mesmo jeito na sugestão do agente e na decisão
 * humana, para que a comparação seja campo a campo.
 */
public final class CamposAvaliacao {

    /** Na triagem, decisão diferente = sugestão descartada. */
    public static final String DECISIVO_TRIAGEM = "decisao";

    private CamposAvaliacao() {
    }

    /** Com SOLICITAR_COMPLEMENTACAO só a decisão conta (categoria e prioridade não são aplicadas). */
    public static Map<String, String> triagem(DecisaoTriagem decisao, CategoriaFiscalizacao categoria,
                                              Prioridade prioridade) {
        if (decisao != DecisaoTriagem.CLASSIFICAR) {
            return GovernancaIaService.campos("decisao", nome(decisao));
        }
        return GovernancaIaService.campos("decisao", nome(decisao), "categoria", nome(categoria),
                "prioridade", nome(prioridade));
    }

    public static Map<String, String> distribuicao(Long equipeId, Long fiscalId, LocalDate prazo) {
        return GovernancaIaService.campos("equipeId", equipeId == null ? null : equipeId.toString(),
                "fiscalId", fiscalId == null ? null : fiscalId.toString(), "prazo", prazo == null ? null : prazo.toString());
    }

    private static String nome(Enum<?> e) {
        return e == null ? null : e.name();
    }
}
