package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import java.util.Locale;

/** Utilidades de vetor: normalização L2, cosseno e o literal aceito por {@code TO_VECTOR} no Oracle 23ai. */
public final class Vetores {

    private Vetores() {
    }

    public static float[] normalizar(float[] v) {
        double soma = 0;
        for (float x : v) {
            soma += (double) x * x;
        }
        double n = Math.sqrt(soma);
        if (n == 0 || Double.isNaN(n)) {
            return v;
        }
        float[] r = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            r[i] = (float) (v[i] / n);
        }
        return r;
    }

    /** Similaridade de cosseno (−1 a 1). */
    public static double cosseno(float[] a, float[] b) {
        double p = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            p += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : p / Math.sqrt(na * nb);
    }

    /** Texto "[0.1,0.2,...]" para {@code TO_VECTOR(?, 768, FLOAT32)} (sempre com ponto decimal). */
    public static String literal(float[] v) {
        StringBuilder s = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                s.append(',');
            }
            float x = Float.isFinite(v[i]) ? v[i] : 0f;
            s.append(String.format(Locale.ROOT, "%.7g", x));
        }
        return s.append(']').toString();
    }
}
