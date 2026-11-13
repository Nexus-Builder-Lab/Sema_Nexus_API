package br.com.nexusbuilderlab.semanexus.docia.assistente;

import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.ReferenciaDocumento;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.RespostaAssistente;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Provider padrão: resposta extrativa (transcreve os trechos recuperados), sem IA generativa. */
@Component
public class DemoAssistenteProvider implements AssistenteProvider {

    public static final String MODO = "Modo demonstrativo — sem IA generativa";

    @Override
    public RespostaAssistente responder(String pergunta, List<ReferenciaDocumento> contexto) {
        String corpo = contexto.stream()
                .map(r -> "[" + r.titulo() + "] " + r.trecho())
                .collect(Collectors.joining("\n"));
        return new RespostaAssistente("Trechos encontrados nos documentos:\n" + corpo, MODO, contexto);
    }
}
