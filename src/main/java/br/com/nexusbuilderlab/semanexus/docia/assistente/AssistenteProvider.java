package br.com.nexusbuilderlab.semanexus.docia.assistente;

import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.ReferenciaDocumento;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.RespostaAssistente;
import java.util.List;

/** Provider desacoplado do assistente DocIA: recebe a pergunta e os trechos recuperados dos documentos. */
public interface AssistenteProvider {

    /** {@code contexto} nunca é vazio (o serviço trata o caso "sem informação suficiente"). */
    RespostaAssistente responder(String pergunta, List<ReferenciaDocumento> contexto);
}
