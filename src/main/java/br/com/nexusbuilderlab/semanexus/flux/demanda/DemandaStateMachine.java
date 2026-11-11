package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Máquina de estados da demanda (docs/ARCHITECTURE.md §5). Concentra as transições válidas; qualquer ação
 * fora do status esperado resulta em {@link ConflitoException} (HTTP 409). A autorização por perfil fica nos controllers.
 */
@Component
public class DemandaStateMachine {

    /** Ações que movem a demanda no fluxo. */
    public enum Acao {
        INICIAR_TRIAGEM("iniciar a triagem"),
        CLASSIFICAR("concluir a triagem"),
        SOLICITAR_COMPLEMENTACAO("solicitar complementação"),
        DISTRIBUIR("distribuir"),
        INICIAR_EXECUCAO("iniciar a execução"),
        REGISTRAR_FISCALIZACAO("registrar a fiscalização"),
        CONCLUIR("concluir"),
        DEVOLVER("devolver");

        private final String descricao;

        Acao(String descricao) {
            this.descricao = descricao;
        }

        public String descricao() {
            return descricao;
        }
    }

    private record Transicao(StatusDemanda de, StatusDemanda para) {
    }

    private static final Map<Acao, Transicao> TRANSICOES = new EnumMap<>(Acao.class);

    static {
        TRANSICOES.put(Acao.INICIAR_TRIAGEM, new Transicao(StatusDemanda.PENDENTE, StatusDemanda.EM_TRIAGEM));
        TRANSICOES.put(Acao.CLASSIFICAR,
                new Transicao(StatusDemanda.EM_TRIAGEM, StatusDemanda.AGUARDANDO_DISTRIBUICAO));
        TRANSICOES.put(Acao.SOLICITAR_COMPLEMENTACAO, new Transicao(StatusDemanda.EM_TRIAGEM, StatusDemanda.PENDENTE));
        TRANSICOES.put(Acao.DISTRIBUIR,
                new Transicao(StatusDemanda.AGUARDANDO_DISTRIBUICAO, StatusDemanda.DISTRIBUIDA));
        TRANSICOES.put(Acao.INICIAR_EXECUCAO, new Transicao(StatusDemanda.DISTRIBUIDA, StatusDemanda.EM_ANDAMENTO));
        TRANSICOES.put(Acao.REGISTRAR_FISCALIZACAO,
                new Transicao(StatusDemanda.EM_ANDAMENTO, StatusDemanda.AGUARDANDO_CONCLUSAO));
        TRANSICOES.put(Acao.CONCLUIR, new Transicao(StatusDemanda.AGUARDANDO_CONCLUSAO, StatusDemanda.FINALIZADA));
        TRANSICOES.put(Acao.DEVOLVER, new Transicao(StatusDemanda.AGUARDANDO_CONCLUSAO, StatusDemanda.EM_ANDAMENTO));
    }

    /** Status resultante da ação ou 409 se ela não é permitida no status atual. */
    public StatusDemanda transicionar(StatusDemanda atual, Acao acao) {
        Transicao t = TRANSICOES.get(acao);
        if (t.de() != atual) {
            throw new ConflitoException("Não é possível " + acao.descricao() + ": a demanda está em \""
                    + atual.rotulo() + "\" e a ação exige \"" + t.de().rotulo() + "\".");
        }
        return t.para();
    }

    public boolean permitida(StatusDemanda atual, Acao acao) {
        return TRANSICOES.get(acao).de() == atual;
    }
}
