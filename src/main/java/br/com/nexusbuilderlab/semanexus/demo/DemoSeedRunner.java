package br.com.nexusbuilderlab.semanexus.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Aplica o seed demonstrativo na subida da aplicação (profiles {@code demo} e {@code dev}). Em qualquer
 * outro profile este bean não é carregado. Não registra senhas nem hashes.
 */
@Component
@Profile(DemoSeedService.PROFILES)
public class DemoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    private final DemoSeedService seed;

    public DemoSeedRunner(DemoSeedService seed) {
        this.seed = seed;
    }

    @Override
    public void run(ApplicationArguments args) {
        ResultadoSeed r = seed.aplicar();
        if (r.vazio()) {
            log.info("Seed demonstrativo já existente — nada a criar. AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.");
        } else {
            log.info("Seed demonstrativo aplicado: {} setor(es), {} usuário(s), {} equipe(s), {} vínculo(s) de membro. "
                    + "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.", r.setores(), r.usuarios(), r.equipes(), r.membros());
        }
    }
}
