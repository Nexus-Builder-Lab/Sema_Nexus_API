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
    private static final Pattern P_AREA = Pattern.compile("(" + NUM + ")\\s*(hectares?|ha|km²|km2|m²|m2)(?![\\p{L}\\d/³])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern P_VAZAO = Pattern.compile("(" + NUM + ")\\s*(m³/s|m3/s|m³/h|m3/h|m³/dia|m3/dia|l/s"
            + "|litros por segundo)(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_VALOR = Pattern.compile("R\\$\\s*(" + NUM + ")(\\s*(milhões|milhão|milhoes"
            + "|milhao|mil)(?![\\p{L}\\d]))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_DATA = Pattern.compile("(?<![\\d/])(\\d{1,2})/(\\d{1,2})/(\\d{4}|\\d{2})(?![\\d/])");
    private static final Pattern CONTEXTO_PRAZO = Pattern.compile("(?i)prazo|vig[êe]ncia|validade|dentro de"
            + "|em at[ée]|no m[áa]ximo|por (at[ée] )?$");
    private static final Pattern P_PRAZO = Pattern.compile("(" + NUM + ")\\s*(dias|dia|meses|mês|mes|anos|ano|horas"
            + "|hora)(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PERC = Pattern.compile("(" + NUM + ")\\s*%");
    private static final Pattern P_CONCLUSAO = Pattern.compile("(?i)\\W*(conclus|parecer final|decis[ãa]o|recomenda-se"
            + "|deferid|indeferid|favor[áa]vel|opina-se|conclui-se)");
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
            double limiar = cat.equals(CONCLUSAO) ? 0.2 : 0.5;
            List<Item> sobraA = new ArrayList<>();
            for (Item x : ia) {
                Item melhor = null;
                double melhorSim = 0;
                for (Item y : ib) {
                    double s = similaridade(x.rotulo(), y.rotulo());
                    if (cat.equals(CONCLUSAO)) {
                        s = similaridade(x.bruto(), y.bruto());
                    }
                    if (s >= limiar && s > melhorSim) {
                        melhor = y;
                        melhorSim = s;
                    }
                }
                if (melhor == null) {
                    sobraA.add(x);
                } else {
                    ib.remove(melhor);
                    r.add(grandeza("ALTERADO", x, melhor));
                }
            }
            sobraA.forEach(x -> r.add(grandeza("REMOVIDO", x, null)));
            ib.forEach(y -> r.add(grandeza("ADICIONADO", null, y)));
        }
        return new Resultado(r, iguais);
    }

    /** Jaccard das palavras; dois textos vazios são tratados como iguais (sem rótulo para distinguir). */
    static double similaridade(String a, String b) {
        Set<String> sa = palavras(a);
        Set<String> sb = palavras(b);
        if (sa.isEmpty() && sb.isEmpty()) {
            return 1;
        }
        Set<String> uniao = new java.util.HashSet<>(sa);
        uniao.addAll(sb);
        sa.retainAll(sb);
        return uniao.isEmpty() ? 0 : (double) sa.size() / uniao.size();
    }

    private static Set<String> palavras(String s) {
        Set<String> r = new java.util.HashSet<>();
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 -]", " ");
        for (String w : n.split("\\s+")) {
            if (w.length() > 1 && !VAZIAS.contains(w)) {
                r.add(w);
            }
        }
        return r;
    }

    private static Grandeza grandeza(String situacao, Item x, Item y) {
        Double abs = null;
        Double pct = null;
        if (x != null && y != null && x.valor() != null && y.valor() != null && x.unidade().equals(y.unidade())) {
            abs = arred(y.valor() - x.valor());
            pct = x.valor() == 0 || x.categoria().equals(DATA) ? null : arred((y.valor() - x.valor()) / x.valor() * 100);
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
            String u = m.group(2).toLowerCase(Locale.ROOT);
            double fator = u.startsWith("km") ? 100 : u.startsWith("m") && !u.startsWith("ha") ? 0.0001 : 1;
            r.add(item(AREA, t, m, arred4(numero(m.group(1)) * fator), "ha"));
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
            try {
                int ano = Integer.parseInt(m.group(3));
                ano = ano < 100 ? 2000 + ano : ano;
                java.time.LocalDate d = java.time.LocalDate.of(ano, Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(1)));
                r.add(item(DATA, t, m, (double) d.toEpochDay(), "dias"));
            } catch (java.time.DateTimeException e) {
                // data impossível (ex.: 31/02): ignorada
            }
        }
        for (Matcher m = P_PRAZO.matcher(t); m.find();) {
            if (!CONTEXTO_PRAZO.matcher(antes(t, m)).find()) {
                continue;
            }
            String u = m.group(2).toLowerCase(Locale.ROOT);
            u = u.startsWith("dia") ? "dias" : u.startsWith("m") ? "meses" : u.startsWith("an") ? "anos" : "horas";
            r.add(item(PRAZO, t, m, numero(m.group(1)), u));
        }
        for (Matcher m = P_PERC.matcher(t); m.find();) {
            r.add(item(PERCENTUAL, t, m, numero(m.group(1)), "%"));
        }
        for (String linha : t.split("\\R")) {
            for (String s : linha.split("(?<=[.;!?])\\s+")) {
                if (!s.isBlank() && P_CONCLUSAO.matcher(s).lookingAt()) {
                    String x = s.strip().replaceAll("\\s+", " ");
                    r.add(new Item(CONCLUSAO, x.length() > 300 ? x.substring(0, 300) + "…" : x, null, "texto", ""));
                }
            }
        }
        return r;
    }

    private static double arred4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /** Texto da mesma frase que antecede o número (até 60 caracteres). */
    private static String antes(String t, Matcher m) {
        String antes = t.substring(Math.max(0, m.start() - 60), m.start());
        int corte = Math.max(antes.lastIndexOf('\n'), Math.max(antes.lastIndexOf(". "), antes.lastIndexOf("; ")));
        return corte >= 0 ? antes.substring(corte + 1) : antes;
    }

    private static Item item(String cat, String t, Matcher m, Double valor, String unidade) {
        return new Item(cat, m.group().strip(), valor, unidade, rotulo(antes(t, m)));
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
                : s.matches("[1-9]\\d{0,2}(\\.\\d{3})+") ? s.replace(".", "") : s;
        return Double.valueOf(x);
    }
}
