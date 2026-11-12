package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Carrega o snapshot público do INPE na subida (profiles demo/dev), sem internet. Falha não impede a subida. */
@Component
@Profile("(demo | dev) & !openapi")
@Order(30)
public class ReferenciaSateliteRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReferenciaSateliteRunner.class);

    private final ReferenciaSateliteLoader loader;

    public ReferenciaSateliteRunner(ReferenciaSateliteLoader loader) {
        this.loader = loader;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            loader.carregarSnapshot();
        } catch (RuntimeException e) {
            log.error("Falha ao carregar a camada de referência do INPE (a API segue sem ela): {}", e.toString());
        }
    }
}
