package br.com.nexusbuilderlab.semanexus.agentes.governanca;

import java.util.List;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Conjunto de casos fixos de regressão/adversariais de um agente (Issue #131). Cada implementação roda só a lógica
 * determinística do agente (regras locais, sanitização, recusa) sobre fixtures em memória: <b>sem chamar modelo e sem
 * ler ou gravar dados operacionais</b>.
 */
public interface CasosAvaliacao {

    /** Nome do agente avaliado (ex.: triagem). */
    String agente();

    List<Caso> executar();

    enum Tipo { REGRESSAO, ADVERSARIAL }

    @Schema(description = "Resultado de um caso da bateria")
    record Caso(@Schema(example = "triagem") String agente,
                @Schema(example = "TRI-R01") String id,
                Tipo tipo,
                @Schema(description = "O que o caso verifica") String descricao,
                String esperado,
                String obtido,
                boolean aprovado) {

        public static Caso de(String agente, String id, Tipo tipo, String descricao, Object esperado, Object obtido) {
            String e = String.valueOf(esperado);
            String o = String.valueOf(obtido);
            return new Caso(agente, id, tipo, descricao, e, o, Objects.equals(e, o));
        }
    }
}
