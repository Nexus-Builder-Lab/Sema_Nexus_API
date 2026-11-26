package br.com.nexusbuilderlab.semanexus.agentes.governanca;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;

import org.springframework.stereotype.Service;

/**
 * Executa a bateria determinística dos agentes (Issue #131): junta os {@link CasosAvaliacao} registrados, roda em
 * memória e devolve o relatório. Sem repositórios nem JDBC por construção — a única gravação é a auditoria.
 */
@Service
public class BateriaAvaliacaoService {

    public static final String MODO = "DETERMINISTICO";

    private final List<CasosAvaliacao> conjuntos;
    private final AuditService auditoria;
    private final Clock clock;

    public BateriaAvaliacaoService(List<CasosAvaliacao> conjuntos, AuditService auditoria, Clock clock) {
        this.conjuntos = conjuntos.stream().sorted(Comparator.comparing(CasosAvaliacao::agente)).toList();
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public RelatorioAvaliacao executar() {
        long t0 = System.nanoTime();
        List<CasosAvaliacao.Caso> casos = new ArrayList<>();
        for (CasosAvaliacao c : conjuntos) {
            try {
                casos.addAll(c.executar());
            } catch (RuntimeException e) {
                casos.add(new CasosAvaliacao.Caso(c.agente(), c.agente().toUpperCase() + "-ERRO",
                        CasosAvaliacao.Tipo.REGRESSAO, "Execução do conjunto de casos", "sem exceção",
                        e.getClass().getSimpleName(), false));
            }
        }
        int aprovados = (int) casos.stream().filter(CasosAvaliacao.Caso::aprovado).count();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        RelatorioAvaliacao r = new RelatorioAvaliacao(LocalDateTime.now(clock), MODO, casos.size(), aprovados,
                casos.size() - aprovados, ms, List.copyOf(casos));
        List<String> reprovados = casos.stream().filter(c -> !c.aprovado()).map(CasosAvaliacao.Caso::id).toList();
        auditoria.registrar("AGENTES_AVALIAR", "AGENTE", "bateria",
                "Bateria determinística: " + aprovados + "/" + casos.size() + " casos aprovados"
                        + (reprovados.isEmpty() ? "." : "; reprovados: " + String.join(", ", reprovados) + "."));
        return r;
    }
}
