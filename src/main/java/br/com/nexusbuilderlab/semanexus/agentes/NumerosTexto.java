package br.com.nexusbuilderlab.semanexus.agentes;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Conferência dos números escritos pela IA contra os dados devolvidos pelas ferramentas (Issue #139). Aceita formatos
 * pt-BR ("1.234,5", "4,5") e com ponto decimal ("4.5"); marcadores de lista ("1. ", "2) ") não contam.
 */
public final class NumerosTexto {

    private static final Pattern NUMERO = Pattern.compile("\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?|\\d+(?:[.,]\\d+)?");
    private static final Pattern MARCADOR_LISTA = Pattern.compile("(?m)^\\s*\\d{1,2}[.)]\\s");

    private NumerosTexto() {
    }

    /** Todos os números (e textos com números) encontrados no JSON, em todas as leituras possíveis. */
    public static List<Double> coletar(JsonNode n) {
        List<Double> r = new ArrayList<>();
        coletar(n, r);
        return r;
    }

    private static void coletar(JsonNode n, List<Double> r) {
        if (n == null) {
            return;
        }
        if (n.isNumber()) {
            r.add(n.asDouble());
        } else if (n.isTextual()) {
            r.addAll(doTexto(n.asText()));
        } else if (n.isContainerNode()) {
            n.forEach(x -> coletar(x, r));
        }
    }

    /** Números de um texto livre. */
    public static List<Double> doTexto(String s) {
        List<Double> r = new ArrayList<>();
        if (s == null) {
            return r;
        }
        Matcher m = NUMERO.matcher(s);
        while (m.find()) {
            r.addAll(leituras(m.group()));
        }
        return r;
    }

    /** true quando todo número do texto (fora marcadores de lista) existe em {@code permitidos}. */
    public static boolean conferem(String texto, List<Double> permitidos) {
        String limpo = MARCADOR_LISTA.matcher(texto == null ? "" : texto).replaceAll(" ");
        Matcher m = NUMERO.matcher(limpo);
        while (m.find()) {
            List<Double> leituras = leituras(m.group());
            if (leituras.stream().noneMatch(n -> permitidos.stream().anyMatch(x -> Math.abs(x - n) < 0.011))) {
                return false;
            }
        }
        return true;
    }

    /** true quando o texto não tem nenhum número. */
    public static boolean semNumeros(String texto) {
        return texto == null || !NUMERO.matcher(MARCADOR_LISTA.matcher(texto).replaceAll(" ")).find();
    }

    /** Leituras possíveis de um número escrito (pt-BR "1.234,5"/"4,5" ou com ponto decimal "4.5"). */
    static List<Double> leituras(String s) {
        List<Double> r = new ArrayList<>();
        try {
            if (s.contains(",")) {
                r.add(Double.valueOf(s.replace(".", "").replace(',', '.')));
            } else if (s.matches("\\d{1,3}(\\.\\d{3})+")) {
                r.add(Double.valueOf(s.replace(".", "")));
                r.add(Double.valueOf(s));
            } else {
                r.add(Double.valueOf(s));
            }
        } catch (NumberFormatException e) {
            // sem leitura válida
        }
        return r;
    }
}
