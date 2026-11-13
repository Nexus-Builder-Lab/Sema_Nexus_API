package br.com.nexusbuilderlab.semanexus.docia.assistente;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.nexusbuilderlab.semanexus.agentes.AgenteIndisponivelException;
import br.com.nexusbuilderlab.semanexus.agentes.AgentesConfiguracao;
import br.com.nexusbuilderlab.semanexus.agentes.AgentesController;
import br.com.nexusbuilderlab.semanexus.agentes.DefinicaoAgente;
import br.com.nexusbuilderlab.semanexus.agentes.ExecutorAgente;
import br.com.nexusbuilderlab.semanexus.agentes.FerramentaAgente;
import br.com.nexusbuilderlab.semanexus.agentes.ResultadoAgente;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.ReferenciaDocumento;
import br.com.nexusbuilderlab.semanexus.docia.assistente.dto.RespostaAssistente;
import br.com.nexusbuilderlab.semanexus.docia.documento.Documento;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoSpecification;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;

/**
 * Assistente DocIA agêntico (Issue #108): o modelo decide o que buscar e ler com as ferramentas
 * {@code buscar_documentos} e {@code ler_documento} e responde com citações. A API valida as referências: só
 * documentos que as ferramentas devolveram, título vindo do banco e trecho conferido no texto do documento.
 * Em qualquer falha lança {@link AgenteIndisponivelException} (o serviço usa o modo extrativo).
 */
@Component
public class AgenteDocia {

    static final String NOME = "docia";
    static final int LIMITE_LEITURA = 4000;
    static final int MAX_RESULTADOS = 5;

    static final String INSTRUCOES = """
            Você é o Assistente DocIA do SEMA NEXUS, um sistema DEMONSTRATIVO (todos os documentos são fictícios).
            Responda à pergunta usando SOMENTE os documentos da biblioteca.
            1. Chame buscar_documentos com 1 a 3 palavras-chave da pergunta.
            2. Chame ler_documento no documento mais relevante (no máximo 2 documentos).
            3. Responda em português, em até 4 frases, citando o título do documento.
            Em referencias informe o documentoId lido e um trecho copiado do texto (até 250 caracteres).
            Se os documentos não tiverem a resposta, use suficiente=false.
            Não oriente sobre leis, multas ou procedimentos oficiais.""";

    static final Map<String, Object> ESQUEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "resposta", Map.of("type", "string", "maxLength", 900),
                    "suficiente", Map.of("type", "boolean"),
                    "referencias", Map.of("type", "array", "maxItems", 3, "items", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "documentoId", Map.of("type", "integer"),
                                    "trecho", Map.of("type", "string", "maxLength", 300)),
                            "required", List.of("documentoId", "trecho"),
                            "additionalProperties", false))),
            "required", List.of("resposta", "suficiente", "referencias"),
            "additionalProperties", false);

    private final ExecutorAgente executor;
    private final AgentesConfiguracao configuracao;
    private final DocumentoRepository documentos;
    private final BuscaDocumentos busca;

    public AgenteDocia(ExecutorAgente executor, AgentesConfiguracao configuracao, DocumentoRepository documentos,
                       BuscaDocumentos busca) {
        this.executor = executor;
        this.configuracao = configuracao;
        this.documentos = documentos;
        this.busca = busca;
    }

    public boolean disponivel() {
        return executor.iaDisponivel();
    }

    /** Métrica do Painel do sistema: pergunta respondida pelo modo extrativo (sem IA ou após falha). */
    public void contarModoExtrativo() {
        executor.contarDemonstrativo(NOME);
    }

    /** Executa o agente; devolve {@code null} quando ele conclui que os documentos não respondem à pergunta. */
    public RespostaAssistente responder(String pergunta) {
        Set<Long> vistos = new LinkedHashSet<>();
        ResultadoAgente r = executor.executar(definicao(vistos), "Pergunta: " + pergunta, "pergunta");
        JsonNode j = r.resposta();
        // Ids internos não interessam ao usuário: o modelo às vezes os escreve no texto.
        String resposta = j.path("resposta").asText("").replaceAll("\\s*\\(?\\b[Dd]ocumento[Ii]d\\b:?\\s*\\d+\\)?", "").strip();
        String modo = configuracao.provedor() == AgentesConfiguracao.Provedor.ANTHROPIC
                ? "Agente DocIA — Claude (Anthropic)"
                : "Agente DocIA — IA local (modelo aberto no servidor, sem custo; os dados não saem do servidor)";
        String agente = configuracao.provedor() == AgentesConfiguracao.Provedor.ANTHROPIC
                ? AgentesController.MODO_CLAUDE : AgentesController.MODO_LOCAL;
        List<String> passos = r.passos().stream().map(ResultadoAgente.PassoAgente::resumo).toList();
        if (!j.path("suficiente").asBoolean(true) || resposta.isEmpty()) {
            return new RespostaAssistente(AssistenteService.SEM_INFORMACAO, modo, List.of(), agente, r.modelo(),
                    passos);
        }
        List<String> termos = AssistenteService.termos(DocumentoSpecification.normalizar(pergunta));
        List<ReferenciaDocumento> referencias = new ArrayList<>();
        for (JsonNode ref : j.path("referencias")) {
            long id = ref.path("documentoId").asLong(-1);
            if (!vistos.contains(id) || referencias.stream().anyMatch(x -> x.documentoId() == id)) {
                continue; // só documentos que as ferramentas devolveram
            }
            documentos.buscarAtivo(id).ifPresent(d ->
                    referencias.add(new ReferenciaDocumento(d.getId(), d.getTitulo(),
                            trechoConferido(d, ref.path("trecho").asText(""), termos))));
        }
        if (referencias.isEmpty() && !vistos.isEmpty()) {
            // O modelo respondeu sem citar: cita o primeiro documento que ele consultou.
            documentos.buscarAtivo(vistos.iterator().next()).ifPresent(d ->
                    referencias.add(new ReferenciaDocumento(d.getId(), d.getTitulo(),
                            trechoConferido(d, "", termos))));
        }
        if (referencias.isEmpty()) {
            throw new AgenteIndisponivelException("O agente respondeu sem consultar documentos.");
        }
        return new RespostaAssistente(resposta, modo, referencias, agente, r.modelo(), passos);
    }

    private DefinicaoAgente definicao(Set<Long> vistos) {
        FerramentaAgente buscar = new FerramentaAgente("buscar_documentos",
                "Busca textual na biblioteca de documentos (sem acento/caixa). Devolve até 5 documentos com id, título "
                        + "e o trecho mais aderente.",
                Map.of("consulta", Map.of("type", "string", "description", "Palavras-chave da pergunta")),
                List.of("consulta"), e -> {
                    String consulta = e.path("consulta").asText("");
                    List<Map<String, Object>> achados = new ArrayList<>();
                    for (BuscaDocumentos.Achado a : busca.buscar(consulta, MAX_RESULTADOS)) {
                        vistos.add(a.documento().getId());
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("documentoId", a.documento().getId());
                        m.put("titulo", a.documento().getTitulo());
                        m.put("trecho", a.trecho());
                        achados.add(m);
                    }
                    return new FerramentaAgente.Resultado(achados,
                            "Buscou \"" + consulta + "\": " + achados.size() + " documento(s).");
                });
        FerramentaAgente ler = new FerramentaAgente("ler_documento",
                "Lê título, descrição, palavras-chave e o texto (até 4.000 caracteres) de um documento pelo id.",
                Map.of("documentoId", Map.of("type", "integer", "description", "Id devolvido por buscar_documentos")),
                List.of("documentoId"), e -> {
                    long id = e.path("documentoId").asLong(-1);
                    Documento d = documentos.buscarAtivo(id)
                            .orElseThrow(() -> new IllegalArgumentException("documento " + id + " não encontrado"));
                    vistos.add(d.getId());
                    String texto = d.getTextoExtraido() == null ? "" : d.getTextoExtraido();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("documentoId", d.getId());
                    m.put("titulo", d.getTitulo());
                    m.put("descricao", d.getDescricao());
                    m.put("palavrasChave", d.getPalavrasChave());
                    m.put("texto", texto.length() > LIMITE_LEITURA ? texto.substring(0, LIMITE_LEITURA) + "…" : texto);
                    return new FerramentaAgente.Resultado(m, "Leu \"" + d.getTitulo() + "\".");
                });
        return new DefinicaoAgente(NOME, configuracao.modeloDocia(), INSTRUCOES, List.of(buscar, ler), ESQUEMA,
                "medium", 900, 2);
    }

    /** Usa o trecho do modelo só se ele existe no documento; senão, o trecho mais aderente aos termos da pergunta. */
    static String trechoConferido(Documento d, String trecho, List<String> termos) {
        String t = trecho == null ? "" : trecho.strip().replaceAll("^[\"“]|[\"”]$", "").replace("…", "").strip();
        if (t.length() >= 20) {
            String alvo = DocumentoSpecification.normalizar(String.join(" ", nulo(d.getTitulo()),
                    nulo(d.getDescricao()), nulo(d.getPalavrasChave()), nulo(d.getTextoExtraido())))
                    .replaceAll("\\s+", " ");
            if (alvo.contains(DocumentoSpecification.normalizar(t).replaceAll("\\s+", " "))) {
                return t;
            }
        }
        String melhor = AssistenteService.melhorTrecho(d, termos);
        return melhor != null ? melhor : d.getTitulo();
    }

    private static String nulo(String s) {
        return s == null ? "" : s;
    }
}
