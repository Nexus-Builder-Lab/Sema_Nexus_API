package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import java.util.ArrayList;
import java.util.List;

/**
 * Divide o documento em trechos (chunks) para os embeddings (Issue #141). O 1º trecho traz título, descrição e
 * palavras-chave; o texto extraído é dividido por frases em blocos de até {@link #TAMANHO} caracteres, com a última
 * frase do bloco anterior repetida no seguinte (sobreposição) para não perder contexto na fronteira. As linhas do selo
 * "AMBIENTE DEMONSTRATIVO" saem: estão em todos os documentos e só aproximariam vetores sem relação.
 */
public final class Fatiador {

    public static final int TAMANHO = 900;
    /** Limite da coluna {@code TEXTO} (VARCHAR2(2000 CHAR)) com folga. */
    static final int MAX_TRECHO = 1800;
    static final int MAX_TRECHOS = 200;

    private Fatiador() {
    }

    public static List<String> fatiar(String titulo, String descricao, String palavrasChave, String texto) {
        List<String> r = new ArrayList<>();
        String cabecalho = String.join("\n", naoVazios(titulo, descricao, palavrasChave)).strip();
        if (!cabecalho.isEmpty()) {
            r.add(cortar(cabecalho));
        }
        String corpo = texto == null ? "" : texto.replaceAll("(?m)^.*AMBIENTE DEMONSTRATIVO.*$\\R?", "")
                .replaceAll("(?<![.!?:;])\\R(?=\\p{Ll})", " ").strip();
        if (corpo.isEmpty()) {
            return r;
        }
        List<String> frases = new ArrayList<>();
        for (String f : corpo.split("(?<=[.!?])\\s+|\\R+")) {
            String s = f.strip();
            while (s.length() > TAMANHO) { // frase gigante (tabela, texto sem pontuação)
                frases.add(s.substring(0, TAMANHO));
                s = s.substring(TAMANHO).strip();
            }
            if (!s.isEmpty()) {
                frases.add(s);
            }
        }
        StringBuilder atual = new StringBuilder();
        String ultima = null;
        for (String f : frases) {
            if (atual.length() > 0 && atual.length() + 1 + f.length() > TAMANHO) {
                r.add(cortar(atual.toString()));
                atual.setLength(0);
                if (ultima != null && ultima.length() + 1 + f.length() <= TAMANHO) {
                    atual.append(ultima);
                }
                if (r.size() >= MAX_TRECHOS) {
                    return r;
                }
            }
            if (atual.length() > 0) {
                atual.append(' ');
            }
            atual.append(f);
            ultima = f;
        }
        if (atual.length() > 0 && r.size() < MAX_TRECHOS) {
            r.add(cortar(atual.toString()));
        }
        return r;
    }

    private static List<String> naoVazios(String... partes) {
        List<String> r = new ArrayList<>();
        for (String p : partes) {
            if (p != null && !p.isBlank()) {
                r.add(p.strip());
            }
        }
        return r;
    }

    private static String cortar(String s) {
        return s.length() > MAX_TRECHO ? s.substring(0, MAX_TRECHO) : s;
    }
}
