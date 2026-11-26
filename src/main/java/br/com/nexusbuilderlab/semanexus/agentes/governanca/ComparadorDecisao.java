package br.com.nexusbuilderlab.semanexus.agentes.governanca;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compara os campos sugeridos por um agente com os aplicados pela pessoa (Issue #131). Determinístico e sem estado.
 * <ul>
 *   <li>Nenhum campo diferente → {@link ResultadoAvaliacao#ACEITA_INTEGRAL}.</li>
 *   <li>Campo decisivo diferente (ex.: a decisão da triagem) ou todos os campos diferentes →
 *   {@link ResultadoAvaliacao#DESCARTADA}.</li>
 *   <li>Caso contrário → {@link ResultadoAvaliacao#AJUSTADA_PARCIAL}.</li>
 * </ul>
 * Só os campos presentes na sugestão são comparados; valor nulo na sugestão conta como sugerido "nada".
 */
public final class ComparadorDecisao {

    /** Resultado e a lista (na ordem da sugestão) dos campos em que a pessoa divergiu. */
    public record Comparacao(ResultadoAvaliacao resultado, List<String> camposDivergentes) {
    }

    private ComparadorDecisao() {
    }

    public static Comparacao comparar(Map<String, String> sugerido, Map<String, String> aplicado,
                                      String campoDecisivo) {
        Map<String, String> s = sugerido == null ? Map.of() : sugerido;
        Map<String, String> a = aplicado == null ? Map.of() : aplicado;
        List<String> divergentes = new ArrayList<>();
        for (Map.Entry<String, String> e : s.entrySet()) {
            if (!Objects.equals(e.getValue(), a.get(e.getKey()))) {
                divergentes.add(e.getKey());
            }
        }
        ResultadoAvaliacao r;
        if (divergentes.isEmpty()) {
            r = ResultadoAvaliacao.ACEITA_INTEGRAL;
        } else if ((campoDecisivo != null && divergentes.contains(campoDecisivo)) || divergentes.size() == s.size()) {
            r = ResultadoAvaliacao.DESCARTADA;
        } else {
            r = ResultadoAvaliacao.AJUSTADA_PARCIAL;
        }
        return new Comparacao(r, Collections.unmodifiableList(divergentes));
    }

    /** Serializa os campos como {@code chave=valor;chave=valor} (valores nulos viram vazio). */
    public static String serializar(Map<String, String> campos) {
        if (campos == null || campos.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        campos.forEach((k, v) -> {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(k).append('=').append(v == null ? "" : v.replace(";", ",").replace("=", ":"));
        });
        return sb.toString();
    }

    /** Inverso de {@link #serializar(Map)}: vazio vira nulo, preservando a ordem. */
    public static Map<String, String> desserializar(String texto) {
        Map<String, String> m = new LinkedHashMap<>();
        if (texto == null || texto.isBlank()) {
            return m;
        }
        for (String par : texto.split(";")) {
            int i = par.indexOf('=');
            if (i > 0) {
                String v = par.substring(i + 1);
                m.put(par.substring(0, i), v.isEmpty() ? null : v);
            }
        }
        return m;
    }
}
