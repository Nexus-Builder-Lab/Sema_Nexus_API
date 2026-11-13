package br.com.nexusbuilderlab.semanexus.docia.assistente;

import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.ReferenciaDocumento;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.RespostaAssistente;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Provider externo (HTTP), ativado só por {@code DOCIA_PROVIDER=external} com {@code DOCIA_API_URL} (e opcionalmente
 * {@code DOCIA_API_KEY}, enviada como Bearer). Envia {@code {pergunta, contexto[]}} e espera {@code {resposta}}.
 * Qualquer falha cai no provider demonstrativo, que informa o próprio modo.
 */
public class ExternalAssistenteProvider implements AssistenteProvider {

    public static final String MODO = "Provider externo configurado";
    private static final Logger LOG = LoggerFactory.getLogger(ExternalAssistenteProvider.class);

    private final RestClient cliente;
    private final String apiKey;
    private final AssistenteProvider alternativo;

    public ExternalAssistenteProvider(RestClient cliente, String apiKey, AssistenteProvider alternativo) {
        this.cliente = cliente;
        this.apiKey = apiKey;
        this.alternativo = alternativo;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RespostaAssistente responder(String pergunta, List<ReferenciaDocumento> contexto) {
        try {
            Map<String, Object> corpo = Map.of("pergunta", pergunta, "contexto", contexto);
            RestClient.RequestBodySpec req = cliente.post().contentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                req = req.header("Authorization", "Bearer " + apiKey);
            }
            Map<String, Object> resp = req.body(corpo).retrieve().body(Map.class);
            Object texto = resp == null ? null : resp.get("resposta");
            if (texto instanceof String s && !s.isBlank()) {
                return new RespostaAssistente(s, MODO, contexto);
            }
        } catch (RuntimeException e) {
            LOG.warn("Provider externo do DocIA indisponível; usando modo demonstrativo ({})",
                    e.getClass().getSimpleName());
        }
        return alternativo.responder(pergunta, contexto);
    }
}
