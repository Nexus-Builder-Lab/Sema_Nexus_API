package br.com.nexusbuilderlab.semanexus.docia.comparacao;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Algoritmos determinísticos de comparação de texto (LCS, Jaccard). Sem IA. */
final class TextoDiff {

    static final String IGUAL = "IGUAL";
    static final String REMOVIDO = "REMOVIDO";
    static final String ADICIONADO = "ADICIONADO";

    record Op(String tipo, String texto) {
    }

    private TextoDiff() {
    }

    /** Chave de comparação: minúsculas, sem acentos e sem pontuação nas pontas. */
    static String chave(String token) {
        String s = Normalizer.normalize(token, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String limpo = s.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
        return limpo.isEmpty() ? s : limpo;
    }

    static List<String> palavras(String texto) {
        List<String> r = new ArrayList<>();
        for (String t : texto.trim().split("\\s+")) {
            if (!t.isEmpty()) {
                r.add(t);
            }
        }
        return r;
    }

    static List<String> linhas(String texto) {
        List<String> r = new ArrayList<>();
        for (String l : texto.split("\\R")) {
            String t = l.trim();
            if (!t.isEmpty()) {
                r.add(t);
            }
        }
        return r;
    }

    /** Diff por LCS entre duas sequências (comparadas por {@link #chave}). */
    static List<Op> diff(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        String[] ka = a.stream().map(TextoDiff::chave).toArray(String[]::new);
        String[] kb = b.stream().map(TextoDiff::chave).toArray(String[]::new);
        int[][] l = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                l[i][j] = ka[i].equals(kb[j]) ? l[i + 1][j + 1] + 1 : Math.max(l[i + 1][j], l[i][j + 1]);
            }
        }
        List<Op> ops = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (ka[i].equals(kb[j])) {
                ops.add(new Op(IGUAL, b.get(j)));
                i++;
                j++;
            } else if (l[i + 1][j] >= l[i][j + 1]) {
                ops.add(new Op(REMOVIDO, a.get(i++)));
            } else {
                ops.add(new Op(ADICIONADO, b.get(j++)));
            }
        }
        while (i < n) {
            ops.add(new Op(REMOVIDO, a.get(i++)));
        }
        while (j < m) {
            ops.add(new Op(ADICIONADO, b.get(j++)));
        }
        return ops;
    }

    /** Junta operações consecutivas do mesmo tipo, separando as palavras por espaço. */
    static List<Op> agrupar(List<Op> ops) {
        List<Op> r = new ArrayList<>();
        StringBuilder atual = null;
        String tipo = null;
        for (Op op : ops) {
            if (op.tipo().equals(tipo)) {
                atual.append(' ').append(op.texto());
            } else {
                if (tipo != null) {
                    r.add(new Op(tipo, atual.toString()));
                }
                tipo = op.tipo();
                atual = new StringBuilder(op.texto());
            }
        }
        if (tipo != null) {
            r.add(new Op(tipo, atual.toString()));
        }
        return r;
    }

    static long iguais(List<Op> ops) {
        return ops.stream().filter(o -> IGUAL.equals(o.tipo())).count();
    }

    /** 2*LCS/(nA+nB) em percentual (0 a 100), arredondado a 1 casa; ambos vazios = 100. */
    static double similaridadeLcs(int na, int nb, long lcs) {
        if (na + nb == 0) {
            return 100.0;
        }
        return arredondar(200.0 * lcs / (na + nb));
    }

    static Set<String> termos(String texto) {
        Set<String> s = new TreeSet<>();
        for (String p : palavras(texto)) {
            String k = chave(p);
            if (k.length() >= 3 || k.matches("\\d+")) {
                s.add(k);
            }
        }
        return s;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 100.0;
        }
        Set<String> inter = new TreeSet<>(a);
        inter.retainAll(b);
        int uniao = a.size() + b.size() - inter.size();
        return arredondar(100.0 * inter.size() / uniao);
    }

    static double arredondar(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
