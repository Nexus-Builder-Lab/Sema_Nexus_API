package br.com.nexusbuilderlab.semanexus.docia.assistente;

import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.PerguntaRequest;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.ReferenciaDocumento;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.TurnoHistorico;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.util.LinkedHashSet;
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
    private final DemandaService demandas;
    private final JdbcTemplate jdbc;

    /** {@code agente} nulo = só o modo extrativo/provider (testes e configuração sem agentes). */
    @Autowired
    public AssistenteService(BuscaDocumentos busca, AssistenteProvider provider, AgenteDocia agente,
                             DemandaService demandas, JdbcTemplate jdbc) {
        this.busca = busca;
        this.provider = provider;
        this.agente = agente;
        this.demandas = demandas;
        this.jdbc = jdbc;
    }

    public AssistenteService(BuscaDocumentos busca, AssistenteProvider provider, AgenteDocia agente) {
        this(busca, provider, agente, null, null);
    }

    public AssistenteService(JdbcTemplate jdbc, DocumentoRepository documentos, AssistenteProvider provider) {
        this(new BuscaDocumentos(jdbc, documentos), provider, null);
    }

    /**
     * Com IA disponível, o Agente DocIA (#108) busca e lê os documentos e responde com citações; sem IA ou em falha,
     * recuperação textual + provider (extrativo por padrão). Sem transação própria: a auditoria do agente grava.
     */
    public RespostaAssistente perguntar(UsuarioAutenticado ator, PerguntaRequest pedido) {
        Set<Long> permitidos = null;
        if (pedido.demandaId() != null) {
            // 404 se a demanda não existe; 403 se o fiscal está fora do escopo (mesma regra do detalhe da demanda).
            demandas.detalhar(ator, pedido.demandaId());
            permitidos = new LinkedHashSet<>(jdbc.queryForList(
                    "SELECT DOCUMENTO_ID FROM DOCUMENTO_DEMANDA WHERE DEMANDA_ID = ?", Long.class, pedido.demandaId()));
            if (permitidos.isEmpty()) {
                return semInformacao(); // nada vinculado: não gasta a IA
            }
        }
        return perguntar(pedido.pergunta(), permitidos, HistoricoConversa.sanitizar(pedido.historico()));
    }

    public RespostaAssistente perguntar(String pergunta) {
        return perguntar(pergunta, null, List.of());
    }

    RespostaAssistente perguntar(String pergunta, Set<Long> permitidos, List<TurnoHistorico> historico) {
        String normalizada = DocumentoSpecification.normalizar(pergunta.strip());
        if (RECUSAVEL.matcher(normalizada).find()) {
            return new RespostaAssistente(RECUSA, modo(), List.of());
        }
        if (agente != null && agente.disponivel()) {
            try {
                return agente.responder(pergunta.strip(), permitidos, historico);
            } catch (AgenteIndisponivelException e) {
                // segue no modo extrativo, que já identifica o próprio modo
            }
        }
        if (agente != null) {
            agente.contarModoExtrativo();
        }
        if (termos(normalizada).isEmpty() || (permitidos != null && permitidos.isEmpty())) {
            return semInformacao();
        }
        BuscaDocumentos.Resultado resultado = busca.pesquisar(pergunta, MAX_DOCUMENTOS, permitidos);
        List<ReferenciaDocumento> contexto = resultado.achados().stream()
                .map(a -> new ReferenciaDocumento(a.documento().getId(), a.documento().getTitulo(), a.trecho(),
                        a.aderencia(), a.similaridadeSemantica()))
                .toList();
        if (contexto.isEmpty()) {
            return semInformacao().comRecuperacao(resultado.recuperacao());
        }
        RespostaAssistente r = provider.responder(pergunta, contexto);
        return r == null ? null : r.comRecuperacao(resultado.recuperacao());
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
            // Frase quebrada no meio da linha ("totalizando 210" / "hectares") volta a ser uma frase só.
            String texto = d.getTextoExtraido().replaceAll("(?<![.!?:;])\\R(?=\\p{Ll})", " ");
            // A linha do título já aparece ao lado do trecho: repeti-la esconderia a frase com o conteúdo.
            String titulo = d.getTitulo() == null ? "" : DocumentoSpecification.normalizar(d.getTitulo().strip());
            for (String frase : texto.split("(?<=[.!?])\\s+|\\R+")) {
                if (!DocumentoSpecification.normalizar(frase.strip()).equals(titulo)) {
                    candidatos.add(frase);
                }
            }
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
