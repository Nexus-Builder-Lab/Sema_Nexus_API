package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoSpecification;

/**
 * Gerador de embeddings <b>falso e determinístico</b> para testes offline (Issue #141): cada palavra (sem acento e
 * caixa, 3+ letras) e seus trigramas de caracteres são espalhados por hash em {@link EmbeddingsConfiguracao#DIMENSAO}
 * posições, mais um punhado de sinônimos de teste ({@link #CONCEITOS}). Aproxima textos com palavras, pedaços de
 * palavras ("desmatada" ~ "desmatamento") e esses sinônimos em comum. Não mede a qualidade do modelo real: só exercita
 * a indexação, a fusão RRF e a abstenção.
 */
public class EmbeddingsDeterministicos implements ClienteEmbeddings {

    public static final String MODELO = "teste-deterministico";

    /**
     * Pequena tabela de sinônimos <b>de teste</b> (radical → conceito) que imita, de forma grosseira e transparente, o
     * que um modelo semântico faz: "derrubada" e "desmatamento" caem no mesmo conceito. Não é usada em produção.
     */
    static final Map<String, String> CONCEITOS = Map.ofEntries(
            Map.entry("desmat", "c-desmatamento"), Map.entry("derrub", "c-desmatamento"),
            Map.entry("supress", "c-desmatamento"),
            Map.entry("florest", "c-vegetacao"), Map.entry("vegeta", "c-vegetacao"),
            Map.entry("queima", "c-fogo"), Map.entry("incend", "c-fogo"), Map.entry("fogo", "c-fogo"),
            Map.entry("seca", "c-seca"), Map.entry("estiag", "c-seca"),
            Map.entry("lixo", "c-residuo"), Map.entry("residu", "c-residuo"),
            Map.entry("outorga", "c-permissao"), Map.entry("licenc", "c-permissao"),
            Map.entry("captac", "c-uso-agua"), Map.entry("irrigac", "c-uso-agua"));
    private boolean disponivel = true;
    private int chamadas;

    public void disponivel(boolean d) {
        this.disponivel = d;
    }

    public int chamadas() {
        return chamadas;
    }

    @Override
    public List<float[]> gerar(List<String> textos, Finalidade finalidade, boolean interativo) {
        chamadas++;
        if (!disponivel) {
            throw new EmbeddingsIndisponiveisException("indisponível (teste)");
        }
        List<float[]> r = new ArrayList<>();
        for (String t : textos) {
            r.add(vetor(t));
        }
        return r;
    }

    public static float[] vetor(String texto) {
        float[] v = new float[EmbeddingsConfiguracao.DIMENSAO];
        for (String p : DocumentoSpecification.normalizar(texto == null ? "" : texto).split("[^a-z0-9]+")) {
            if (p.length() < 3) {
                continue;
            }
            v[Math.floorMod(p.hashCode(), v.length)] += 2f;
            for (Map.Entry<String, String> c : CONCEITOS.entrySet()) {
                if (p.startsWith(c.getKey())) {
                    v[Math.floorMod(c.getValue().hashCode(), v.length)] += 4f;
                }
            }
            String w = "#" + p + "#";
            for (int i = 0; i + 3 <= w.length(); i++) {
                v[Math.floorMod(w.substring(i, i + 3).hashCode() * 31 + 7, v.length)] += 1f;
            }
        }
        return Vetores.normalizar(v);
    }

    @Override
    public String modelo() {
        return MODELO;
    }

    @Override
    public boolean disponivel() {
        return disponivel;
    }
}
