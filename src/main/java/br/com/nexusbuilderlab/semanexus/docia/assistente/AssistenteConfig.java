package br.com.nexusbuilderlab.semanexus.docia.assistente;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Escolhe o provider por variáveis de ambiente; sem configuração completa, usa o modo demonstrativo. */
@Configuration
class AssistenteConfig {

    @Bean
    @Primary
    AssistenteProvider assistenteProvider(DemoAssistenteProvider demo,
                                          @Value("${DOCIA_PROVIDER:demo}") String provider,
                                          @Value("${DOCIA_API_URL:}") String url,
                                          @Value("${DOCIA_API_KEY:}") String chave) {
        if ("external".equalsIgnoreCase(provider.strip()) && !url.isBlank()) {
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(Duration.ofSeconds(5));
            f.setReadTimeout(Duration.ofSeconds(20));
            return new ExternalAssistenteProvider(
                    RestClient.builder().baseUrl(url.strip()).requestFactory(f).build(), chave, demo);
        }
        return demo;
    }
}
