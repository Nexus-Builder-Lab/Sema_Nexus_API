package br.com.nexusbuilderlab.semanexus.security;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Limites de requisição (Issue #134), só por variáveis de ambiente, com padrões seguros para a demonstração:
 * {@code LIMITE_REQUISICOES_ATIVO} (true), {@code LIMITE_JANELA_SEGUNDOS} (60), {@code LIMITE_LOGIN_POR_JANELA} (20,
 * por IP), {@code LIMITE_IA_POR_JANELA} (10, por usuário) e {@code LIMITE_UPLOAD_POR_JANELA} (20, por usuário).
 */
@Configuration
public class LimiteRequisicoesConfig {

    @Bean
    LimitadorRequisicoes limitadorRequisicoes(@Value("${LIMITE_REQUISICOES_ATIVO:true}") boolean ativo,
                                              @Value("${LIMITE_LOGIN_POR_JANELA:20}") int login,
                                              @Value("${LIMITE_IA_POR_JANELA:10}") int ia,
                                              @Value("${LIMITE_UPLOAD_POR_JANELA:20}") int upload,
                                              @Value("${LIMITE_JANELA_SEGUNDOS:60}") int janelaSegundos,
                                              Clock clock) {
        return new LimitadorRequisicoes(ativo, login, ia, upload, Duration.ofSeconds(janelaSegundos), clock);
    }
}
