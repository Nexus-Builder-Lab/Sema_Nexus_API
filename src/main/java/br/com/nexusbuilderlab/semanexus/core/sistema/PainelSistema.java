package br.com.nexusbuilderlab.semanexus.core.sistema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import br.com.nexusbuilderlab.semanexus.agentes.MetricasAgentes;

import io.swagger.v3.oas.annotations.media.Schema;

/** Painel do sistema (Issue #114): verificações ao vivo e operação dos agentes. Nunca contém segredos. */
@Schema(description = "Painel do sistema (somente ADMIN): saúde ao vivo dos componentes e operação dos agentes")
public record PainelSistema(LocalDateTime geradoEm, Sistema sistema, List<Verificacao> verificacoes,
                            List<AgentePainel> agentes) {

    public enum Estado { OK, ATENCAO, INDISPONIVEL }

    @Schema(description = "Dados da aplicação em execução")
    public record Sistema(String versao, String perfilAtivo, String java, LocalDateTime iniciadoEm,
                          long segundosNoAr, long memoriaUsadaMb, long memoriaMaximaMb) {
    }

    @Schema(description = "Verificação de um componente")
    public record Verificacao(@Schema(example = "banco") String id, String nome, Estado estado,
                              @Schema(nullable = true) Long latenciaMs, String resumo, Map<String, Object> detalhes) {
    }

    @Schema(description = "Agente de IA e suas métricas desde que a API subiu")
    public record AgentePainel(String id, String nome, String modulo, String modo, String modelo,
                               List<String> ferramentas, MetricasAgentes.Resumo metricas) {
    }
}
