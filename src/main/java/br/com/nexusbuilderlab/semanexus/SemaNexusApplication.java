package br.com.nexusbuilderlab.semanexus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SEMA ALERTA — API.
 *
 * <p>Plataforma demonstrativa de inteligência ambiental, gestão de demandas de fiscalização
 * e gestão documental no contexto de Mato Grosso. Utiliza exclusivamente dados fictícios e
 * não possui integração com sistemas institucionais.
 */
@SpringBootApplication
public class SemaNexusApplication {

    public static void main(String[] args) {
        SpringApplication.run(SemaNexusApplication.class, args);
    }
}
