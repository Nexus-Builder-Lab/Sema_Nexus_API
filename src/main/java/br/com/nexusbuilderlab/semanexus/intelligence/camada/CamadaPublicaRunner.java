package br.com.nexusbuilderlab.semanexus.intelligence.camada;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Carrega os snapshots offline de camadas públicas de MT na subida (profiles demo/dev), sem rede. */
@Component
@Profile("(demo | dev) & !openapi")
@Order(35)
public class CamadaPublicaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CamadaPublicaRunner.class);

    private final CamadaPublicaLoader loader;

    public CamadaPublicaRunner(CamadaPublicaLoader loader) {
        this.loader = loader;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            loader.carregarSnapshot();
        } catch (RuntimeException e) {
            log.error("Falha ao carregar as camadas públicas de MT (a API segue sem elas): {}", e.toString());
        }
    }
}
