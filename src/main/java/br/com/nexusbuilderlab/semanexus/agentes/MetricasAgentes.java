package br.com.nexusbuilderlab.semanexus.agentes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Métricas dos agentes desde que a API subiu (memória, sem banco): execuções com IA, sucessos, falhas, usos do modo
 * demonstrativo e tempo médio. Alimenta o Painel do sistema (Issue #114). Nada sensível é guardado.
 */
@Component
public class MetricasAgentes {

    /** Fotografia das métricas de um agente. */
    public record Resumo(String agente, long execucoes, long sucessos, long falhas, long demonstrativo,
                         Long tempoMedioMs, Long ultimoTempoMs, LocalDateTime ultimaExecucao, String ultimoModelo,
                         String ultimaFalha) {
    }

    private static final class Contador {
        long sucessos;
        long falhas;
        long demonstrativo;
        long somaMs;
        Long ultimoMs;
        LocalDateTime ultima;
        String modelo;
        String falha;
    }

    private final Map<String, Contador> porAgente = new ConcurrentHashMap<>();

    public void sucesso(String agente, String modelo, long ms) {
        Contador c = porAgente.computeIfAbsent(agente, k -> new Contador());
        synchronized (c) {
            c.sucessos++;
            c.somaMs += ms;
            c.ultimoMs = ms;
            c.ultima = LocalDateTime.now();
            c.modelo = modelo;
        }
    }

    public void falha(String agente, String modelo, long ms, String motivo) {
        Contador c = porAgente.computeIfAbsent(agente, k -> new Contador());
        synchronized (c) {
            c.falhas++;
            c.ultimoMs = ms;
            c.ultima = LocalDateTime.now();
            c.modelo = modelo;
            c.falha = motivo;
        }
    }

    public void demonstrativo(String agente) {
        Contador c = porAgente.computeIfAbsent(agente, k -> new Contador());
        synchronized (c) {
            c.demonstrativo++;
            c.ultima = LocalDateTime.now();
        }
    }

    public Resumo resumo(String agente) {
        Contador c = porAgente.get(agente);
        if (c == null) {
            return new Resumo(agente, 0, 0, 0, 0, null, null, null, null, null);
        }
        synchronized (c) {
            return new Resumo(agente, c.sucessos + c.falhas, c.sucessos, c.falhas, c.demonstrativo,
                    c.sucessos == 0 ? null : c.somaMs / c.sucessos, c.ultimoMs, c.ultima, c.modelo, c.falha);
        }
    }

    public List<Resumo> todos(List<String> agentes) {
        return agentes.stream().map(this::resumo).toList();
    }
}
