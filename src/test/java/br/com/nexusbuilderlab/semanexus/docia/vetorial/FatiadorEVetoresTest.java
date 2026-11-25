package br.com.nexusbuilderlab.semanexus.docia.vetorial;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Divisão em trechos e utilidades de vetor da busca híbrida (Issue #141). */
class FatiadorEVetoresTest {

    @Test
    void cabecalhoNoPrimeiroTrechoESeloDemonstrativoForaDoTexto() {
        List<String> t = Fatiador.fatiar("Laudo — Primavera", "Captação sem outorga", "laudo, outorga",
                "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.\nVazão estimada: 12 litros por segundo.\n"
                        + "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.");
        assertThat(t).hasSize(2);
        assertThat(t.get(0)).isEqualTo("Laudo — Primavera\nCaptação sem outorga\nlaudo, outorga");
        assertThat(t.get(1)).isEqualTo("Vazão estimada: 12 litros por segundo.");
    }

    @Test
    void textoLongoViraVariosTrechosComSobreposicaoEDentroDoLimite() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            s.append("Frase número ").append(i).append(" do relatório fictício de vistoria ambiental. ");
        }
        List<String> t = Fatiador.fatiar("Relatório", null, null, s.toString());
        assertThat(t.size()).isGreaterThan(3);
        assertThat(t).allSatisfy(x -> assertThat(x.length()).isLessThanOrEqualTo(Fatiador.TAMANHO));
        // A última frase de um trecho abre o seguinte (contexto na fronteira).
        String ultima = t.get(1).substring(t.get(1).lastIndexOf("Frase número"));
        assertThat(t.get(2)).startsWith(ultima.strip());
    }

    @Test
    void fraseGiganteSemPontuacaoEhCortada() {
        List<String> t = Fatiador.fatiar("T", null, null, "x".repeat(5000));
        assertThat(t).allSatisfy(x -> assertThat(x.length()).isLessThanOrEqualTo(Fatiador.MAX_TRECHO));
        assertThat(String.join("", t.subList(1, t.size())).length()).isGreaterThanOrEqualTo(5000);
    }

    @Test
    void documentoSemNadaNaoGeraTrecho() {
        assertThat(Fatiador.fatiar(null, " ", null, null)).isEmpty();
    }

    @Test
    void literalDoVetorUsaPontoDecimalEColchetes() {
        String l = Vetores.literal(new float[] {0.5f, -0.25f, Float.NaN});
        assertThat(l).startsWith("[").endsWith("]").doesNotContain("NaN");
        assertThat(l.split(",")).hasSize(3);
        assertThat(l.split(",")[0]).isEqualTo("[0.5000000");
    }

    @Test
    void normalizacaoECosseno() {
        float[] a = Vetores.normalizar(new float[] {3, 4});
        assertThat(a[0]).isEqualTo(0.6f);
        assertThat(Vetores.cosseno(a, a)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(Vetores.cosseno(new float[] {1, 0}, new float[] {0, 1})).isZero();
        assertThat(Vetores.normalizar(new float[] {0, 0})).containsExactly(0, 0);
    }
}
