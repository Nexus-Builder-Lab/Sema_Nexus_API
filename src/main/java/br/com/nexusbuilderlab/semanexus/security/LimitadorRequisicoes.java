package br.com.nexusbuilderlab.semanexus.security;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de requisições por usuário/IP (Issue #134), em janela fixa e em memória (uma única instância da API na
 * demonstração). Categorias: {@code LOGIN} (por IP), {@code IA} e {@code UPLOAD} (por usuário autenticado; sem usuário,
 * por IP). Excedido o limite, a resposta é 429 com {@code Retry-After} = segundos até a janela seguinte.
 * Os contadores somem a cada reinício — aceitável para proteção contra abuso, que não precisa de histórico.
 */
public class LimitadorRequisicoes {

    /** Grupo de endpoints com limite próprio. */
    public enum Categoria { LOGIN, IA, UPLOAD }

    /** Resultado do consumo: permitido ou, se bloqueado, os segundos até liberar. */
    public record Decisao(boolean permitido, long retryAfterSegundos) {
        static final Decisao PERMITIDO = new Decisao(true, 0);
    }

    /** Acima deste número de chaves, as janelas vencidas são descartadas (memória limitada). */
    static final int LIMITE_CHAVES = 10_000;

    private record Janela(long inicioMs, int contagem) {}

    private final boolean ativo;
    private final Map<Categoria, Integer> limites;
    private final long janelaMs;
    private final Clock clock;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    public LimitadorRequisicoes(boolean ativo, int login, int ia, int upload, Duration janela, Clock clock) {
        this.ativo = ativo;
        Map<Categoria, Integer> m = new EnumMap<>(Categoria.class);
        m.put(Categoria.LOGIN, Math.max(1, login));
        m.put(Categoria.IA, Math.max(1, ia));
        m.put(Categoria.UPLOAD, Math.max(1, upload));
        this.limites = Map.copyOf(m);
        this.janelaMs = Math.max(1_000, Math.min(janela.toMillis(), 3_600_000));
        this.clock = clock;
    }

    public boolean ativo() {
        return ativo;
    }

    public int limite(Categoria categoria) {
        return limites.get(categoria);
    }

    public long janelaSegundos() {
        return janelaMs / 1000;
    }

    /** Conta uma requisição de {@code chave} (ex.: "u:12" ou "ip:10.0.0.1") na categoria. */
    public Decisao consumir(Categoria categoria, String chave) {
        if (!ativo) {
            return Decisao.PERMITIDO;
        }
        long agora = clock.millis();
        if (janelas.size() > LIMITE_CHAVES) {
            janelas.values().removeIf(j -> agora - j.inicioMs() >= janelaMs);
        }
        int limite = limites.get(categoria);
        Janela j = janelas.compute(categoria + "|" + chave, (k, atual) -> {
            if (atual == null || agora - atual.inicioMs() >= janelaMs) {
                return new Janela(agora, 1);
            }
            // Não passa de limite + 1: o bloqueado não estende a janela nem estoura o contador.
            return new Janela(atual.inicioMs(), Math.min(atual.contagem() + 1, limite + 1));
        });
        if (j.contagem() <= limite) {
            return Decisao.PERMITIDO;
        }
        long restanteMs = j.inicioMs() + janelaMs - agora;
        return new Decisao(false, Math.max(1, (restanteMs + 999) / 1000));
    }
}
