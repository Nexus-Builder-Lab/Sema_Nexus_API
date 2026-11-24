package br.com.nexusbuilderlab.semanexus.docia.comparacao;

import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ExplicacaoComparacaoResponse.Grandeza;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extração determinística de grandezas (áreas, vazões, valores, datas, prazos, percentuais e conclusões) e
 * comparação entre duas versões. Os números da tabela vêm só daqui, nunca do modelo.
 */
final class GrandezasExtrator {

    static final String AREA = "AREA";
    static final String VAZAO = "VAZAO";
    static final String VALOR = "VALOR";
    static final String DATA = "DATA";
    static final String PRAZO = "PRAZO";
    static final String PERCENTUAL = "PERCENTUAL";
    static final String CONCLUSAO = "CONCLUSAO";

    private static final String NUM = "\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?|\\d+(?:[.,]\\d+)?";
    private static final Pattern P_AREA = Pattern.compile("(" + NUM + ")\\s*(hectares?|ha)(?![\\p{L}\\d])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_VAZAO = Pattern.compile("(" + NUM + ")\\s*(m³/s|m3/s|m³/h|m3/h|m³/dia|m3/dia|l/s"
            + "|litros por segundo)(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_VALOR = Pattern.compile("R\\$\\s*(" + NUM + ")(\\s*(milhões|milhão|milhoes"
            + "|milhao|mil)(?![\\p{L}\\d]))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_DATA = Pattern.compile("(?<!\\d)(\\d{1,2})/(\\d{1,2})/(\\d{4})(?!\\d)");
    private static final Pattern P_PRAZO = Pattern.compile("(" + NUM + ")\\s*(dias|dia|meses|mês|mes|anos|ano|horas"
            + "|hora)(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PERC = Pattern.compile("(" + NUM + ")\\s*%");
    private static final Pattern P_CONCLUSAO = Pattern.compile("(?i)conclus|parecer final|decis[ãa]o|recomenda-se"
            + "|deferid|indeferid|favor[áa]vel");
    private static final Set<String> VAZIAS = Set.of("de", "da", "do", "das", "dos", "a", "o", "as", "os", "em",
            "para", "com", "foi", "e", "que", "um", "uma", "no", "na", "por", "ser", "sera");

    /** Item extraído: valor numérico quando houver, texto bruto e rótulo (palavras que antecedem). */
    record Item(String categoria, String bruto, Double valor, String unidade, String rotulo) {
        String chave() {
            return valor != null ? categoria + "|" + valor + "|" + unidade
                    : categoria + "|" + bruto.toLowerCase(Locale.ROOT);
        }
    }

    record Resultado(List<Grandeza> grandezas, int iguais) {
    }

    private GrandezasExtrator() {
    }

    static Resultado comparar(String textoA, String textoB) {
        List<Item> a = extrair(textoA);
        List<Item> b = extrair(textoB);
        List<Grandeza> r = new ArrayList<>();
        int iguais = 0;
        for (String cat : List.of(AREA, VAZAO, VALOR, DATA, PRAZO, PERCENTUAL, CONCLUSAO)) {
            List<Item> ia = new ArrayList<>(a.stream().filter(i -> i.categoria().equals(cat)).toList());
            List<Item> ib = new ArrayList<>(b.stream().filter(i -> i.categoria().equals(cat)).toList());
            for (Iterator<Item> it = ia.iterator(); it.hasNext();) {
                Item x = it.next();
                Item y = ib.stream().filter(z -> z.chave().equals(x.chave())).findFirst().orElse(null);
                if (y != null) {
                    ib.remove(y);
                    it.remove();
                    iguais++;
                }
            }
            int n = Math.min(ia.size(), ib.size());
            for (int i = 0; i < n; i++) {
                r.add(grandeza("ALTERADO", ia.get(i), ib.get(i)));
            }
            for (int i = n; i < ia.size(); i++) {
                r.add(grandeza("REMOVIDO", ia.get(i), null));
            }
            for (int i = n; i < ib.size(); i++) {
                r.add(grandeza("ADICIONADO", null, ib.get(i)));
            }
        }
        return new Resultado(r, iguais);
    }

    private static Grandeza grandeza(String situacao, Item x, Item y) {
        Double abs = null;
        Double pct = null;
        if (x != null && y != null && x.valor() != null && y.valor() != null && x.unidade().equals(y.unidade())) {
            abs = arred(y.valor() - x.valor());
            pct = x.valor() == 0 ? null : arred((y.valor() - x.valor()) / x.valor() * 100);
        }
        Item ref = x != null ? x : y;
        return new Grandeza(ref.categoria(), situacao, x == null ? null : x.rotulo(), x == null ? null : x.bruto(),
                y == null ? null : y.rotulo(), y == null ? null : y.bruto(), abs, pct, ref.unidade());
    }

    private static double arred(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    static List<Item> extrair(String texto) {
        List<Item> r = new ArrayList<>();
        if (texto == null) {
            return r;
        }
        String t = texto.replaceAll("(?m)^.*AMBIENTE DEMONSTRATIVO.*$\\R?", "");
        for (Matcher m = P_AREA.matcher(t); m.find();) {
            r.add(item(AREA, t, m, numero(m.group(1)), "ha"));
        }
        for (Matcher m = P_VAZAO.matcher(t); m.find();) {
            String u = m.group(2).toLowerCase(Locale.ROOT).replace("m3", "m³").replace("litros por segundo", "l/s");
            r.add(item(VAZAO, t, m, numero(m.group(1)), u));
        }
        for (Matcher m = P_VALOR.matcher(t); m.find();) {
            double v = numero(m.group(1));
            String mult = m.group(3) == null ? "" : m.group(3).toLowerCase(Locale.ROOT);
            v *= mult.isEmpty() ? 1 : mult.equals("mil") ? 1_000 : 1_000_000;
            r.add(item(VALOR, t, m, v, "R$"));
        }
        for (Matcher m = P_DATA.matcher(t); m.find();) {
            r.add(item(DATA, t, m, null, "data"));
        }
        for (Matcher m = P_PRAZO.matcher(t); m.find();) {
            String u = m.group(2).toLowerCase(Locale.ROOT);
            u = u.startsWith("dia") ? "dias" : u.startsWith("m") ? "meses" : u.startsWith("an") ? "anos" : "horas";
            r.add(item(PRAZO, t, m, numero(m.group(1)), u));
        }
        for (Matcher m = P_PERC.matcher(t); m.find();) {
            r.add(item(PERCENTUAL, t, m, numero(m.group(1)), "%"));
        }
        for (String linha : t.split("\\R")) {
            for (String s : linha.split("(?<=[.;!?])\\s+")) {
                if (!s.isBlank() && P_CONCLUSAO.matcher(s).find()) {
                    String x = s.strip().replaceAll("\\s+", " ");
                    r.add(new Item(CONCLUSAO, x.length() > 300 ? x.substring(0, 300) + "…" : x, null, "texto", ""));
                }
            }
        }
        return r;
    }

    private static Item item(String cat, String t, Matcher m, Double valor, String unidade) {
        int ini = Math.max(0, m.start() - 60);
        String antes = t.substring(ini, m.start());
        int corte = Math.max(antes.lastIndexOf('\n'), Math.max(antes.lastIndexOf(". "), antes.lastIndexOf("; ")));
        if (corte >= 0) {
            antes = antes.substring(corte + 1);
        }
        return new Item(cat, m.group().strip(), valor, unidade, rotulo(antes));
    }

    static String rotulo(String antes) {
        String n = Normalizer.normalize(antes, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ");
        List<String> p = new ArrayList<>();
        for (String w : n.split("\\s+")) {
            if (!w.isBlank() && !VAZIAS.contains(w)) {
                p.add(w);
            }
        }
        return String.join(" ", p.subList(Math.max(0, p.size() - 3), p.size()));
    }

    static Double numero(String s) {
        String x = s.contains(",") ? s.replace(".", "").replace(',', '.')
                : s.matches("\\d{1,3}(\\.\\d{3})+") ? s.replace(".", "") : s;
        return Double.valueOf(x);
    }
}
