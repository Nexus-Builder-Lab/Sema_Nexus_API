package br.com.nexusbuilderlab.semanexus.agentes;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Escolhe o provedor do modelo pela variável {@code AGENTES_PROVEDOR} (padrão: Ollama local). */
@Configuration
class ProvedorAgentesConfig {

    @Bean
    ClienteModelo clienteModelo(AgentesConfiguracao configuracao, ObjectMapper json) {
        return switch (configuracao.provedor()) {
            case OLLAMA -> new OllamaClienteModelo(configuracao, json);
            case ANTHROPIC -> new AnthropicClienteModelo(configuracao);
            case DEMO -> new SemModelo();
        };
    }

    /** {@code AGENTES_PROVEDOR=demo}: nunca chama modelo; os agentes usam só as regras locais. */
    static final class SemModelo implements ClienteModelo {
        @Override
        public boolean disponivel() {
            return false;
        }

        @Override
        public String modelo(String modeloSugerido) {
            return modeloSugerido;
        }

        @Override
        public RespostaModelo criar(PedidoModelo pedido) {
            throw new AgenteIndisponivelException("Agentes configurados só no modo demonstrativo.");
        }
    }
}
