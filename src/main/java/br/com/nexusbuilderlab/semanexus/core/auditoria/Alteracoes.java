package br.com.nexusbuilderlab.semanexus.core.auditoria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Valores antes → depois de uma edição (Issue #148). Guarda só os campos que de fato mudaram, como texto, na ordem em
 * que foram informados. Nunca informe senha, hash ou token: a auditoria é consultada pelo ADMIN e o texto passa pelo
 * saneamento do {@link AuditService}, mas o ideal é que dado sensível nem chegue aqui.
 */
public final class Alteracoes {

    /** Limite de cada valor (caracteres); o JSON inteiro ainda é limitado a 4000 bytes (tamanho da coluna). */
    static final int LIMITE_VALOR = 300;
    static final int LIMITE_BYTES = 4000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, String> antes = new LinkedHashMap<>();
    private final Map<String, String> depois = new LinkedHashMap<>();

    private Alteracoes() {
    }

    public static Alteracoes nova() {
        return new Alteracoes();
    }

    /** Compara dois retratos (campo → valor) do mesmo objeto, tirados antes e depois da edição. */
    public static Alteracoes entre(Map<String, ?> anterior, Map<String, ?> novo) {
        Alteracoes a = new Alteracoes();
        anterior.forEach((campo, valor) -> a.campo(campo, valor, novo.get(campo)));
        return a;
    }

    /** Registra o campo se {@code anterior} e {@code novo} diferirem (números comparados pelo valor, 1.50 = 1.5). */
    public Alteracoes campo(String nome, Object anterior, Object novo) {
        if (!iguais(anterior, novo)) {
            antes.put(nome, texto(anterior));
            depois.put(nome, texto(novo));
        }
        return this;
    }

    public boolean vazia() {
        return antes.isEmpty();
    }

    public Map<String, String> antes() {
        return Collections.unmodifiableMap(antes);
    }

    public Map<String, String> depois() {
        return Collections.unmodifiableMap(depois);
    }

    String antesJson() {
        return json(antes);
    }

    String depoisJson() {
        return json(depois);
    }

    private static boolean iguais(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        String s = valor instanceof BigDecimal b ? b.stripTrailingZeros().toPlainString() : valor.toString();
        return s.length() <= LIMITE_VALOR ? s : s.substring(0, LIMITE_VALOR - 3) + "...";
    }

    /** JSON objeto; se passar do limite da coluna, encurta os valores e, no extremo, guarda só os nomes dos campos. */
    static String json(Map<String, String> valores) {
        if (valores.isEmpty()) {
            return null;
        }
        String r = serializar(valores);
        if (bytes(r) <= LIMITE_BYTES) {
            return r;
        }
        Map<String, String> curtos = new LinkedHashMap<>();
        valores.forEach((k, v) -> curtos.put(k, v == null || v.length() <= 40 ? v : v.substring(0, 37) + "..."));
        r = serializar(curtos);
        if (bytes(r) <= LIMITE_BYTES) {
            return r;
        }
        Map<String, String> nomes = new LinkedHashMap<>();
        valores.keySet().forEach(k -> nomes.put(k, "(omitido: limite de tamanho)"));
        r = serializar(nomes);
        return bytes(r) <= LIMITE_BYTES ? r : "{\"_omitido\":\"limite de tamanho\"}";
    }

    private static String serializar(Map<String, String> valores) {
        try {
            return JSON.writeValueAsString(valores);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar alterações da auditoria.", e);
        }
    }

    private static int bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}
