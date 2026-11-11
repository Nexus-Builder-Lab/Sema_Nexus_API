package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaStateMachine.Acao;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemandaStateMachineTest {

    static final Map<Acao, StatusDemanda[]> VALIDAS = Map.of(
            Acao.INICIAR_TRIAGEM, new StatusDemanda[]{StatusDemanda.PENDENTE, StatusDemanda.EM_TRIAGEM},
            Acao.CLASSIFICAR, new StatusDemanda[]{StatusDemanda.EM_TRIAGEM, StatusDemanda.AGUARDANDO_DISTRIBUICAO},
            Acao.SOLICITAR_COMPLEMENTACAO, new StatusDemanda[]{StatusDemanda.EM_TRIAGEM, StatusDemanda.PENDENTE},
            Acao.DISTRIBUIR, new StatusDemanda[]{StatusDemanda.AGUARDANDO_DISTRIBUICAO, StatusDemanda.DISTRIBUIDA},
            Acao.INICIAR_EXECUCAO, new StatusDemanda[]{StatusDemanda.DISTRIBUIDA, StatusDemanda.EM_ANDAMENTO},
            Acao.REGISTRAR_FISCALIZACAO,
            new StatusDemanda[]{StatusDemanda.EM_ANDAMENTO, StatusDemanda.AGUARDANDO_CONCLUSAO},
            Acao.CONCLUIR, new StatusDemanda[]{StatusDemanda.AGUARDANDO_CONCLUSAO, StatusDemanda.FINALIZADA},
            Acao.DEVOLVER, new StatusDemanda[]{StatusDemanda.AGUARDANDO_CONCLUSAO, StatusDemanda.EM_ANDAMENTO});

    final DemandaStateMachine maquina = new DemandaStateMachine();

    @Test
    void todasAsTransicoesValidasLevamAoStatusEsperado() {
        VALIDAS.forEach((acao, par) -> {
            assertThat(maquina.permitida(par[0], acao)).isTrue();
            assertThat(maquina.transicionar(par[0], acao)).as(acao.name()).isEqualTo(par[1]);
        });
        assertThat(VALIDAS).hasSize(Acao.values().length);
    }

    @Test
    void todaAcaoForaDoStatusDeOrigemRespondeConflito() {
        for (Acao acao : Acao.values()) {
            for (StatusDemanda status : StatusDemanda.values()) {
                if (status == VALIDAS.get(acao)[0]) {
                    continue;
                }
                assertThat(maquina.permitida(status, acao)).isFalse();
                assertThatThrownBy(() -> maquina.transicionar(status, acao))
                        .as(acao + " em " + status).isInstanceOf(ConflitoException.class);
            }
        }
    }

    @Test
    void cadaStatusRejeitaAoMenosUmaAcao() {
        for (StatusDemanda status : StatusDemanda.values()) {
            long rejeitadas = java.util.Arrays.stream(Acao.values()).filter(a -> !maquina.permitida(status, a)).count();
            assertThat(rejeitadas).as(status.name()).isPositive();
        }
    }

    @Test
    void finalizadaNaoPermiteNenhumaAcao() {
        for (Acao acao : Acao.values()) {
            assertThat(maquina.permitida(StatusDemanda.FINALIZADA, acao)).isFalse();
        }
    }
}
