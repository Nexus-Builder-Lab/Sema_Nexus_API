package br.com.nexusbuilderlab.semanexus.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita tarefas agendadas (ex.: verificação diária de prazos das notificações). */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
