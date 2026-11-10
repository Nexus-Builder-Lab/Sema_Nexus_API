package br.com.nexusbuilderlab.semanexus.demo;

/** Quantidade de registros efetivamente criados por uma execução do seed demonstrativo. */
public record ResultadoSeed(int setores, int usuarios, int equipes, int membros) {

    public boolean vazio() {
        return setores == 0 && usuarios == 0 && equipes == 0 && membros == 0;
    }
}
