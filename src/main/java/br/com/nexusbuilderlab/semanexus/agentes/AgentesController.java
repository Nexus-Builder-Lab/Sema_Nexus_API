package br.com.nexusbuilderlab.semanexus.agentes;

import java.util.List;

import br.com.nexusbuilderlab.semanexus.agentes.AgentesConfiguracao.Provedor;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize(Autorizacao.AUTENTICADO)
@RequestMapping("/api/agentes")
@Tag(name = "Agentes de IA", description = "Agentes que rodam dentro do sistema e sugerem ações (Issue #106)")
public class AgentesController {

    public static final String MODO_LOCAL = "LOCAL";
    public static final String MODO_CLAUDE = "CLAUDE";
    public static final String MODO_DEMONSTRATIVO = "DEMONSTRATIVO";
    static final String SEM_IA = "Regras locais (sem IA generativa)";

    private final AgentesConfiguracao configuracao;
    private final ExecutorAgente executor;

    public AgentesController(AgentesConfiguracao configuracao, ExecutorAgente executor) {
        this.configuracao = configuracao;
        this.executor = executor;
    }

    @Operation(summary = "Modo e modelo de cada agente",
            description = "Qualquer perfil autenticado. `LOCAL` = modelo aberto no Ollama da própria máquina (sem custo); "
                    + "`CLAUDE` = Anthropic (exige chave); `DEMONSTRATIVO` = regras locais, quando não há IA "
                    + "disponível. Nunca expõe chaves.")
    @GetMapping("/status")
    public StatusAgentes status() {
        String modo = modo();
        boolean ia = !MODO_DEMONSTRATIVO.equals(modo);
        String descricao = switch (modo) {
            case MODO_LOCAL -> "Agentes com IA local (modelo aberto no servidor, sem custo e sem enviar dados para "
                    + "fora) — sugestões sempre revisadas por uma pessoa.";
            case MODO_CLAUDE -> "Agentes com Claude (Anthropic) — sugestões sempre revisadas por uma pessoa.";
            default -> "Modo demonstrativo — sugestões por regras locais, sem IA generativa.";
        };
        return new StatusAgentes(modo, descricao, List.of(
                new AgenteInfo("triagem", "Agente de Triagem", "SEMA Flux",
                        ia ? executor.modelo(configuracao.modeloTriagem()) : SEM_IA,
                        "Sugere categoria, prioridade e núcleo regional e aponta possíveis duplicatas."),
                new AgenteInfo("docia", "Assistente DocIA", "SEMA DocIA",
                        ia ? executor.modelo(configuracao.modeloDocia()) : SEM_IA,
                        "Busca e lê documentos e responde com citações.")));
    }

    private String modo() {
        if (!executor.iaDisponivel()) {
            return MODO_DEMONSTRATIVO;
        }
        return configuracao.provedor() == Provedor.ANTHROPIC ? MODO_CLAUDE : MODO_LOCAL;
    }

    @Schema(description = "Modo efetivo dos agentes e modelo de cada um")
    public record StatusAgentes(@Schema(example = "LOCAL") String modo, String descricao, List<AgenteInfo> agentes) {
    }

    @Schema(description = "Agente disponível no sistema")
    public record AgenteInfo(@Schema(example = "triagem") String id, String nome, String modulo,
                             @Schema(example = "qwen3:4b") String modelo, String descricao) {
    }
}
