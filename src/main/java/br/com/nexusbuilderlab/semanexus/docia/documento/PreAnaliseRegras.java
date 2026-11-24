package br.com.nexusbuilderlab.semanexus.docia.documento;

import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.PreAnaliseDocumento;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Regras determinísticas (sem IA, sem gravação) da pré-análise de um arquivo no upload. */
final class PreAnaliseRegras {

    private static final Pattern DEM = Pattern.compile("\\bDEM-(\\d{4})/(\\d{4})\\b");
    private static final Pattern DATA_NUM = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");
    private static final Pattern DATA_ISO = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern DATA_EXT = Pattern.compile(
            "\\b(\\d{1,2})\\s+de\\s+([a-zA-Zçã]+)\\s+de\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final List<String> MESES = List.of("janeiro", "fevereiro", "marco", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro");
    private static final Set<String> STOP = Set.of("para", "como", "com", "uma", "umas", "uns", "que", "dos", "das",
            "nos", "nas", "por", "mais", "foi", "ser", "sao", "seu", "sua", "seus", "suas", "este", "esta", "esse",
            "essa", "isso", "entre", "sobre", "apos", "ainda", "quando", "onde", "pelo", "pela", "pelos",
            "pelas", "aos", "num", "numa", "mato", "grosso", "tambem", "caso", "sera", "nao",
            "sem", "cada", "todo", "toda", "todos", "todas", "demonstrativo", "ficticio", "ficticios", "dados");

    private PreAnaliseRegras() {
    }

    static PreAnaliseDocumento analisar(String nomeArquivo, String texto, List<String> avisos, List<Municipio> municipios) {
        String t = texto == null ? "" : texto;
        Municipio m = municipio(t, municipios);
        return new PreAnaliseDocumento(titulo(nomeArquivo, t), m == null ? null : m.getId(),
                m == null ? null : m.getNome(), data(t), palavrasChave(t), vinculos(t), !t.isBlank(), avisos);
    }

    static String titulo(String nomeArquivo, String texto) {
        for (String linha : texto.split("\\R")) {
            String l = linha.replaceFirst("^[#>*\\-\\s|]+", "").strip();
            if (l.length() >= 5 && l.length() <= 150 && !l.startsWith("---")) {
                return l;
            }
        }
        String base = nomeArquivo == null ? ""
                : nomeArquivo.replaceFirst("\\.[^.]*$", "").replace('_', ' ').replace('-', ' ').strip();
        return base.isEmpty() ? null : base;
    }

    static List<String> vinculos(String texto) {
        Set<String> r = new LinkedHashSet<>();
        Matcher m = DEM.matcher(texto);
        while (m.find()) {
            r.add("DEM-" + m.group(1) + "/" + m.group(2));
        }
        return List.copyOf(r);
    }

    /** Primeira data válida e não futura, na ordem em que aparece no texto. */
    static LocalDate data(String texto) {
        List<int[]> achados = new ArrayList<>(); // {posição, ano, mês, dia}
        Matcher m = DATA_NUM.matcher(texto);
        while (m.find()) {
            achados.add(new int[]{m.start(), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(1))});
        }
        m = DATA_ISO.matcher(texto);
        while (m.find()) {
            achados.add(new int[]{m.start(), Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3))});
        }
        m = DATA_EXT.matcher(texto);
        while (m.find()) {
            int mes = MESES.indexOf(semAcento(m.group(2))) + 1;
            if (mes > 0) {
                achados.add(new int[]{m.start(), Integer.parseInt(m.group(3)), mes, Integer.parseInt(m.group(1))});
            }
        }
        achados.sort((a, b) -> Integer.compare(a[0], b[0]));
        LocalDate hoje = LocalDate.now();
        for (int[] a : achados) {
            try {
                LocalDate d = LocalDate.of(a[1], a[2], a[3]);
                if (!d.isAfter(hoje) && d.getYear() >= 1990) {
                    return d;
                }
            } catch (DateTimeException ignorada) {
                // data impossível (ex.: 31/02): tenta a próxima
            }
        }
        return null;
    }

    /** Município de MT mais citado; em empate, o de nome mais longo (ex.: "Nova Mutum" × "Mutum"). */
    static Municipio municipio(String texto, List<Municipio> municipios) {
        String alvo = " " + semAcento(texto).replaceAll("[^a-z0-9]+", " ") + " ";
        Municipio melhor = null;
        int melhorContagem = 0;
        for (Municipio mu : municipios) {
            String nome = " " + semAcento(mu.getNome()).replaceAll("[^a-z0-9]+", " ").strip() + " ";
            int c = 0;
            for (int i = alvo.indexOf(nome); i >= 0; i = alvo.indexOf(nome, i + nome.length() - 1)) {
                c++;
            }
            if (c > melhorContagem
                    || (c > 0 && c == melhorContagem && mu.getNome().length() > melhor.getNome().length())) {
                melhor = mu;
                melhorContagem = c;
            }
        }
        return melhor;
    }

    static List<String> palavrasChave(String texto) {
        Map<String, Integer> freq = new HashMap<>();
        for (String w : semAcento(texto).split("[^a-z]+")) {
            if (w.length() >= 5 && !STOP.contains(w)) {
                freq.merge(w, 1, Integer::sum);
            }
        }
        return freq.entrySet().stream().filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(8).map(Map.Entry::getKey).toList();
    }

    static String semAcento(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
