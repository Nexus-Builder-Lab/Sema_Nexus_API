package br.com.nexusbuilderlab.semanexus.flux.demanda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Último sequencial de numeração de demanda usado em um ano (lock pessimista na geração do número). */
@Entity
@Table(name = "NUMERACAO_DEMANDA")
public class NumeracaoDemanda {

    @Id
    @Column(name = "ANO")
    private Integer ano;

    @Column(name = "ULTIMO", nullable = false)
    private long ultimo;

    protected NumeracaoDemanda() {
    }

    public NumeracaoDemanda(Integer ano) {
        this.ano = ano;
    }

    /** Avança e devolve o próximo sequencial do ano. */
    public long proximo() {
        return ++ultimo;
    }

    public Integer getAno() {
        return ano;
    }

    public long getUltimo() {
        return ultimo;
    }
}
