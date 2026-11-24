package br.com.nexusbuilderlab.semanexus.security;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Limite de requisições por usuário/IP (Issue #134), em janela fixa e em memória (uma única instância da API na
 * demonstração). Categorias: {@code LOGIN} (por IP), {@code IA} e {@code UPLOAD} (por usuário autenticado; sem usuário,
 * por IP). Excedido o limite, a resposta é 429 com {@code Retry-After} = segundos até a janela seguinte.
 * Os contadores somem a cada reinício — aceitável para proteção contra abuso, que não precisa de histórico.
 *
 * <p>Memória limitada: as janelas ficam num {@link LinkedHashMap} em ordem de início (uma janela renovada volta para o
 * fim). A cada consumo, as vencidas são retiradas do começo — custo amortizado O(1), sem varredura por requisição — e,
 * acima de {@code maxChaves} chaves vivas, a mais antiga é descartada (O(1)). Um acesso sincronizado por vez: o trabalho
 * dentro do bloqueio é constante e curto.
 */
public class LimitadorRequisicoes {

    /** Grupo de endpoints com limite próprio. */
    public enum Categoria { LOGIN, IA, UPLOAD }

    /** Resultado do consumo: permitido ou, se bloqueado, os segundos até liberar. */
    public record Decisao(boolean permitido, long retryAfterSegundos) {
        static final Decisao PERMITIDO = new Decisao(true, 0);
    }

    /** Padrão de chaves vivas (categoria + usuário/IP) guardadas ao mesmo tempo. */
    public static final int MAX_CHAVES_PADRAO = 10_000;

    private static final class Janela {
        final long inicioMs;
        int contagem;

        Janela(long inicioMs) {
            this.inicioMs = inicioMs;
            this.contagem = 1;
        }
    }

    private final boolean ativo;
    private final Map<Categoria, Integer> limites;
    private final long janelaMs;
    private final int maxChaves;
    private final Clock clock;
    /** Ordem de inserção = ordem de início da janela (protegido por {@code this}). */
    private final LinkedHashMap<String, Janela> janelas = new LinkedHashMap<>();

    public LimitadorRequisicoes(boolean ativo, int login, int ia, int upload, Duration janela, Clock clock) {
        this(ativo, login, ia, upload, janela, MAX_CHAVES_PADRAO, clock);
    }

    public LimitadorRequisicoes(boolean ativo, int login, int ia, int upload, Duration janela, int maxChaves,
                                Clock clock) {
        this.ativo = ativo;
        Map<Categoria, Integer> m = new EnumMap<>(Categoria.class);
        m.put(Categoria.LOGIN, Math.max(1, login));
        m.put(Categoria.IA, Math.max(1, ia));
        m.put(Categoria.UPLOAD, Math.max(1, upload));
        this.limites = Map.copyOf(m);
        this.janelaMs = Math.max(1_000, Math.min(janela.toMillis(), 3_600_000));
        this.maxChaves = Math.max(100, Math.min(maxChaves, 1_000_000));
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

    public int maxChaves() {
        return maxChaves;
    }

    /** Chaves vivas no momento (diagnóstico e testes). */
    public synchronized int chavesVivas() {
        return janelas.size();
    }

    /** Conta uma requisição de {@code chave} (ex.: "u:12" ou "ip:10.0.0.1") na categoria. */
    public Decisao consumir(Categoria categoria, String chave) {
        if (!ativo) {
            return Decisao.PERMITIDO;
        }
        int limite = limites.get(categoria);
        String k = categoria + "|" + chave;
        long agora = clock.millis();
        long inicio;
        int contagem;
        synchronized (this) {
            descartarVencidas(agora);
            Janela j = janelas.get(k);
            if (j == null || agora - j.inicioMs >= janelaMs) {
                if (j != null) {
                    janelas.remove(k); // a janela renovada vai para o fim da ordem
                }
                j = new Janela(agora);
                janelas.put(k, j);
                if (janelas.size() > maxChaves) {
                    Iterator<String> maisAntiga = janelas.keySet().iterator();
                    maisAntiga.next();
                    maisAntiga.remove();
                }
            } else if (j.contagem <= limite) {
                // Não passa de limite + 1: o bloqueado não estende a janela nem estoura o contador.
                j.contagem++;
            }
            inicio = j.inicioMs;
            contagem = j.contagem;
        }
        if (contagem <= limite) {
            return Decisao.PERMITIDO;
        }
        long restanteMs = inicio + janelaMs - agora;
        return new Decisao(false, Math.max(1, (restanteMs + 999) / 1000));
    }

    /** Retira do começo da ordem as janelas vencidas; para na primeira ainda válida (amortizado O(1)). */
    private void descartarVencidas(long agora) {
        Iterator<Janela> it = janelas.values().iterator();
        while (it.hasNext()) {
            if (agora - it.next().inicioMs >= janelaMs) {
                it.remove();
            } else {
                return;
            }
        }
    }
}
