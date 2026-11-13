package br.com.nexusbuilderlab.semanexus.docia.assistente;

import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.ReferenciaDocumento;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.RespostaAssistente;
import br.com.nexusbuilderlab.semanexus.docia.documento.Documento;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoSpecification;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import br.com.nexusbuilderlab.semanexus.agentes.AgenteIndisponivelException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Recupera trechos dos documentos por busca textual e delega a resposta ao provider. */
@Service
public class AssistenteService {

    public static final String SEM_INFORMACAO =
            "Não há informação suficiente nos documentos para responder a essa pergunta.";
    public static final String RECUSA = "O assistente responde apenas com base nos documentos da biblioteca. "
            + "Não orienta sobre legislação, multas ou procedimentos oficiais; consulte a fonte oficial.";

    static final int MAX_DOCUMENTOS = 3;
    static final int MAX_TRECHO = 400;
    private static final Pattern RECUSAVEL = Pattern.compile(
            "\\b(lei|leis|legisla\\w*|decreto|portaria|resolucao|multa|multas|multar|penalidade\\w*|sancao|sancoes|"
                    + "autuacao|autuar|infracao|infracoes|procedimento\\w*|artigo)\\b");
    private static final Set<String> VAZIAS = Set.of("para", "com", "sem", "sobre", "que", "qual", "quais", "como",
            "quando", "onde", "quem", "dos", "das", "nos", "nas", "por", "uma", "uns", "umas", "aos", "foi", "sao",
            "ser", "tem", "existe", "existem", "este", "esta", "esse", "essa", "isso", "mais", "meu", "minha",
            "diga", "fale", "documento", "documentos");

    private final BuscaDocumentos busca;
    private final AssistenteProvider provider;
    private final AgenteDocia agente;

    /** {@code agente} nulo = só o modo extrativo/provider (testes e configuração sem agentes). */
    @Autowired
    public AssistenteService(BuscaDocumentos busca, AssistenteProvider provider, AgenteDocia agente) {
        this.busca = busca;
        this.provider = provider;
        this.agente = agente;
    }

    public AssistenteService(JdbcTemplate jdbc, DocumentoRepository documentos, AssistenteProvider provider) {
        this(new BuscaDocumentos(jdbc, documentos), provider, null);
    }

    /**
     * Com IA disponível, o Agente DocIA (#108) busca e lê os documentos e responde com citações; sem IA ou em falha,
     * recuperação textual + provider (extrativo por padrão). Sem transação própria: a auditoria do agente grava.
     */
    public RespostaAssistente perguntar(String pergunta) {
        String normalizada = DocumentoSpecification.normalizar(pergunta.strip());
        if (RECUSAVEL.matcher(normalizada).find()) {
            return new RespostaAssistente(RECUSA, modo(), List.of());
        }
        if (agente != null && agente.disponivel()) {
            try {
                return agente.responder(pergunta.strip());
            } catch (AgenteIndisponivelException e) {
                // segue no modo extrativo, que já identifica o próprio modo
            }
        }
        if (agente != null) {
            agente.contarModoExtrativo();
        }
        if (termos(normalizada).isEmpty()) {
            return semInformacao();
        }
        List<ReferenciaDocumento> contexto = busca.buscar(pergunta, MAX_DOCUMENTOS).stream()
                .map(a -> new ReferenciaDocumento(a.documento().getId(), a.documento().getTitulo(), a.trecho()))
                .toList();
        if (contexto.isEmpty()) {
            return semInformacao();
        }
        return provider.responder(pergunta, contexto);
    }

    private RespostaAssistente semInformacao() {
        return new RespostaAssistente(SEM_INFORMACAO, modo(), List.of());
    }

    private String modo() {
        return provider instanceof ExternalAssistenteProvider ? ExternalAssistenteProvider.MODO
                : DemoAssistenteProvider.MODO;
    }

    static List<String> termos(String normalizada) {
        List<String> r = new ArrayList<>();
        for (String t : normalizada.split("[^a-z0-9]+")) {
            if (t.length() >= 4 && !VAZIAS.contains(t) && !r.contains(t)) {
                r.add(t);
            }
        }
        return r;
    }

    /** Frase (ou linha) do texto, descrição, palavras-chave ou título com mais termos; nulo se nenhum aparece. */
    static String melhorTrecho(Documento d, List<String> termos) {
        List<String> candidatos = new ArrayList<>();
        if (d.getTextoExtraido() != null) {
            candidatos.addAll(List.of(d.getTextoExtraido().split("(?<=[.!?])\\s+|\\R+")));
        }
        candidatos.add(d.getDescricao());
        candidatos.add(d.getPalavrasChave());
        candidatos.add(d.getTitulo());
        String melhor = null;
        int max = 0;
        for (String c : candidatos) {
            if (c == null || c.isBlank()) {
                continue;
            }
            String n = DocumentoSpecification.normalizar(c);
            int acertos = 0;
            for (String t : termos) {
                if (n.contains(t)) {
                    acertos++;
                }
            }
            if (acertos > max) {
                max = acertos;
                melhor = c.strip();
            }
        }
        if (melhor != null && melhor.length() > MAX_TRECHO) {
            melhor = melhor.substring(0, MAX_TRECHO).strip() + "…";
        }
        return melhor;
    }
}
