package br.com.nexusbuilderlab.semanexus.core.municipio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/**
 * Município de Mato Grosso — dado público de referência do IBGE, carregado pela migration
 * {@code V2__municipios_mt.sql}. A PK é o código IBGE. Somente leitura pela aplicação.
 */
@Entity
@Immutable
@Table(name = "MUNICIPIO")
public class Municipio {

    @Id
    @Column(name = "ID")
    private Integer id;

    @Column(name = "NOME", nullable = false, length = 100)
    private String nome;

    @Column(name = "REGIAO", nullable = false, length = 60)
    private String regiao;

    @Enumerated(EnumType.STRING)
    @Column(name = "BIOMA", nullable = false, length = 20)
    private Bioma bioma;

    @Column(name = "LATITUDE", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "LONGITUDE", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    protected Municipio() {
    }

    public Integer getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getRegiao() {
        return regiao;
    }

    public Bioma getBioma() {
        return bioma;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }
}
