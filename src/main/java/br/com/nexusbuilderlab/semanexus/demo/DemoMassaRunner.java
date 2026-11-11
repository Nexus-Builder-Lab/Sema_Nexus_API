package br.com.nexusbuilderlab.semanexus.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Aplica a massa demonstrativa (demandas, fiscalizações, documentos) depois do seed base. Profiles demo/dev. */
@Component
@Profile(DemoSeedService.PROFILES)
@Order(20)
public class DemoMassaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoMassaRunner.class);

    private final DemoMassaService massa;

    public DemoMassaRunner(DemoMassaService massa) {
        this.massa = massa;
    }

    @Override
    public void run(ApplicationArguments args) {
        var r = massa.aplicar();
        if (r.vazio()) {
            log.info("Massa demonstrativa já existente — nada a criar. AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.");
        } else {
            log.info("Massa demonstrativa aplicada: {} demanda(s), {} documento(s), {} vínculo(s). "
                    + "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.", r.demandas(), r.documentos(), r.vinculos());
        }
    }
}
