package br.com.nexusbuilderlab.semanexus.agentes;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Aquece o modelo local ao iniciar a API (Issue #120): carrega o modelo na GPU em segundo plano, para que a primeira
 * pergunta não espere os 30–50 s de carregamento. Não bloqueia a subida; falhas só são registradas (os agentes seguem
 * com o modo demonstrativo até o Ollama responder). Desligável com {@code AGENTES_AQUECER=false}. O aquecimento ocupa
 * uma vaga da {@link FilaGpu} (Issue #134), como qualquer chamada ao modelo: pedidos que chegarem nesse meio-tempo
 * esperam na fila (ou caem no modo demonstrativo após o timeout), sem disputar a GPU fora do controle.
 */
@Component
@Profile("!openapi")
public class AquecimentoModelo {

    private static final Logger LOG = LoggerFactory.getLogger(AquecimentoModelo.class);

    private final ClienteModelo cliente;
    private final AgentesConfiguracao configuracao;
    private final FilaGpu filaGpu;

    /** Espera máxima do aquecimento por uma vaga na fila (ele roda em segundo plano, então pode esperar mais). */
    static final Duration ESPERA_AQUECIMENTO = Duration.ofMinutes(5);

    public AquecimentoModelo(ClienteModelo cliente, AgentesConfiguracao configuracao, FilaGpu filaGpu) {
        this.cliente = cliente;
        this.configuracao = configuracao;
        this.filaGpu = filaGpu;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        if (!configuracao.aquecer() || configuracao.provedor() != AgentesConfiguracao.Provedor.OLLAMA) {
            return;
        }
        Thread t = new Thread(this::aquecer, "aquecimento-modelo");
        t.setDaemon(true);
        t.start();
    }

    void aquecer() {
        long t0 = System.currentTimeMillis();
        boolean ok;
        try {
            ok = filaGpu.executarAguardando(ESPERA_AQUECIMENTO, cliente::aquecer);
        } catch (AgenteIndisponivelException e) {
            ok = false;
        }
        if (ok) {
            LOG.info("Modelo local {} carregado na memória em {} ms (fica {} após o último uso).",
                    configuracao.modeloLocal(), System.currentTimeMillis() - t0, configuracao.manterCarregado());
        } else {
            LOG.info("Aquecimento do modelo local não realizado (Ollama indisponível ou modelo não baixado).");
        }
    }
}
