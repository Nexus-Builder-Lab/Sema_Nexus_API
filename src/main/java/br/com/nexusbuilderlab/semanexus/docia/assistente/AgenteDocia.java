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
 * Desde a Issue #120 o modelo escreve só a resposta (em texto livre ou no esquema curto) e a API monta as citações a
 * partir dos documentos lidos — menos tokens gerados e nenhuma reescrita em JSON. Em qualquer falha lança {@link AgenteIndisponivelException} (o serviço usa o modo extrativo).
 */
@Component
public class AgenteDocia {

    static final String NOME = "docia";
    static final int LIMITE_LEITURA = 4000;
    static final int MAX_RESULTADOS = 5;
    static final int MAX_REFERENCIAS = 2;
    /** Resposta do modelo quando os documentos não respondem à pergunta. */
    static final String NAO_ENCONTRADO = "NAO_ENCONTRADO";

    static final String INSTRUCOES = """
            Você é o Assistente DocIA do SEMA ALERTA, um sistema DEMONSTRATIVO (todos os documentos são fictícios).
            Responda à pergunta usando SOMENTE os documentos da biblioteca.
            1. Chame buscar_documentos com 1 a 3 palavras-chave da pergunta. Ela devolve o texto dos documentos mais             relevantes e a lista dos outros encontrados.
            2. Só se a resposta depender de outro documento da lista (ex.: comparar versões), chame ler_documento nele.
            3. Responda em português, em até 3 frases curtas e diretas, com os fatos do texto e o título do documento.
            Se os textos não tiverem a resposta, responda apenas: NAO_ENCONTRADO
            Não repita avisos de que os dados são fictícios (a tela já mostra) e não escreva ids de documentos.
            Não oriente sobre leis, multas ou procedimentos oficiais.""";

    static final Map<String, Object> ESQUEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "resposta", Map.of("type", "string", "maxLength", 700),
                    "suficiente", Map.of("type", "boolean")),
            "required", List.of("resposta", "suficiente"),
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
        Set<Long> lidos = new LinkedHashSet<>();
        ResultadoAgente r = executor.executar(definicao(vistos, lidos), "Pergunta: " + pergunta, "pergunta");
        JsonNode j = r.resposta();
        // Ids internos não interessam ao usuário: o modelo às vezes os escreve no texto.
        String resposta = j.path("resposta").asText("").replaceAll("\\s*\\(?\\b[Dd]ocumento[Ii]d\\b:?\\s*\\d+\\)?", "").strip();
        resposta = semAvisos(resposta);
        String modo = configuracao.provedor() == AgentesConfiguracao.Provedor.ANTHROPIC
                ? "Agente DocIA — Claude (Anthropic)"
                : "Agente DocIA — IA local (modelo aberto no servidor, sem custo; os dados não saem do servidor)";
        String agente = configuracao.provedor() == AgentesConfiguracao.Provedor.ANTHROPIC
                ? AgentesController.MODO_CLAUDE : AgentesController.MODO_LOCAL;
        List<String> passos = r.passos().stream().map(ResultadoAgente.PassoAgente::resumo).toList();
        if (!j.path("suficiente").asBoolean(true) || resposta.isEmpty() || resposta.contains(NAO_ENCONTRADO)) {
            return new RespostaAssistente(AssistenteService.SEM_INFORMACAO, modo, List.of(), agente, r.modelo(),
                    passos);
        }
        List<String> termos = BuscaDocumentos.radicais(pergunta + " " + resposta);
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
        if (referencias.isEmpty()) {
            // Citações montadas pela API: documentos lidos (ou, sem leitura, o primeiro encontrado), com o trecho do
            // documento mais aderente à pergunta e à resposta.
            List<Long> base = lidos.isEmpty() ? vistos.stream().limit(1).toList()
                    : lidos.stream().limit(MAX_REFERENCIAS).toList();
            for (Long id : base) {
                documentos.buscarAtivo(id).ifPresent(d ->
                        referencias.add(new ReferenciaDocumento(d.getId(), d.getTitulo(),
                                trechoConferido(d, "", termos))));
            }
        }
        if (referencias.isEmpty()) {
            throw new AgenteIndisponivelException("O agente respondeu sem consultar documentos.");
        }
        return new RespostaAssistente(resposta, modo, referencias, agente, r.modelo(), passos);
    }

    private DefinicaoAgente definicao(Set<Long> vistos, Set<Long> lidos) {
        FerramentaAgente buscar = new FerramentaAgente("buscar_documentos",
                "Busca textual na biblioteca de documentos (sem acento/caixa). Devolve o texto dos documentos mais "
                        + "relevantes (lidos: o 1º e um 2º igualmente relevante) e os outros, do mais ao menos "
                        + "relevante, com id, título, "
                        + "relevância (alta/média/baixa) e o trecho mais aderente.",
                Map.of("consulta", Map.of("type", "string", "description", "Palavras-chave da pergunta")),
                List.of("consulta"), e -> {
                    String consulta = e.path("consulta").asText("");
                    List<BuscaDocumentos.Achado> achados = busca.buscar(consulta, MAX_RESULTADOS);
                    Map<String, Object> r = new LinkedHashMap<>();
                    List<Map<String, Object>> outros = new ArrayList<>();
                    List<Map<String, Object>> textos = new ArrayList<>();
                    List<String> titulosLidos = new ArrayList<>();
                    for (BuscaDocumentos.Achado a : achados) {
                        vistos.add(a.documento().getId());
                        // O mais relevante (e um 2º de relevância alta, ex.: outra versão) já vem lido: o modelo
                        // pequeno às vezes respondia só pelo trecho ou não lia a outra versão.
                        if (textos.isEmpty() || (textos.size() < MAX_REFERENCIAS && "alta".equals(a.relevancia()))) {
                            lidos.add(a.documento().getId());
                            textos.add(leitura(a.documento(), textos.isEmpty() ? LIMITE_LEITURA : LIMITE_LEITURA / 2));
                            titulosLidos.add("\"" + a.documento().getTitulo() + "\"");
                            continue;
                        }
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("documentoId", a.documento().getId());
                        m.put("titulo", a.documento().getTitulo());
                        m.put("trecho", a.trecho());
                        m.put("relevancia", a.relevancia());
                        outros.add(m);
                    }
                    r.put("lidos", textos);
                    r.put("outros", outros);
                    return new FerramentaAgente.Resultado(r, "Buscou \"" + consulta + "\": " + achados.size()
                            + " documento(s)"
                            + (titulosLidos.isEmpty() ? "." : "; leu " + String.join(" e ", titulosLidos) + "."));
                });
        FerramentaAgente ler = new FerramentaAgente("ler_documento",
                "Lê título, descrição, palavras-chave e o texto (até 4.000 caracteres) de um documento pelo id.",
                Map.of("documentoId", Map.of("type", "integer", "description", "Id devolvido por buscar_documentos")),
                List.of("documentoId"), e -> {
                    long id = e.path("documentoId").asLong(-1);
                    Documento d = documentos.buscarAtivo(id)
                            .orElseThrow(() -> new IllegalArgumentException("documento " + id + " não encontrado"));
                    vistos.add(d.getId());
                    lidos.add(d.getId());
                    return new FerramentaAgente.Resultado(leitura(d), "Leu \"" + d.getTitulo() + "\".");
                });
        // 3 rodadas = buscar + ler até 2 documentos (com 2 o modelo era cortado antes da 2ª leitura e falhava).
        return new DefinicaoAgente(NOME, configuracao.modeloDocia(), INSTRUCOES, List.of(buscar, ler), ESQUEMA,
                "medium", 400, 3, "resposta");
    }

    /**
     * Conteúdo entregue ao modelo. As linhas do selo "AMBIENTE DEMONSTRATIVO" saem do texto: a tela já mostra o selo e,
     * no texto, elas gastavam tokens e faziam o modelo repetir o aviso na resposta.
     */
    static Map<String, Object> leitura(Documento d) {
        return leitura(d, LIMITE_LEITURA);
    }

    static Map<String, Object> leitura(Documento d, int limite) {
        String texto = d.getTextoExtraido() == null ? "" : d.getTextoExtraido()
                .replaceAll("(?m)^.*AMBIENTE DEMONSTRATIVO.*$\\R?", "").strip();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("documentoId", d.getId());
        m.put("titulo", d.getTitulo());
        m.put("descricao", d.getDescricao());
        m.put("palavrasChave", d.getPalavrasChave());
        m.put("texto", texto.length() > limite ? texto.substring(0, limite) + "…" : texto);
        return m;
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

    /** Aviso de "dado fictício" que o modelo repete: a tela já mostra o selo, então a frase sai da resposta. */
    private static final java.util.regex.Pattern AVISO = java.util.regex.Pattern.compile(
            "(?i).*(sem valor oficial|fict[ií]ci|demonstrativ).*");

    static String semAvisos(String resposta) {
        StringBuilder r = new StringBuilder();
        for (String f : resposta.split("(?<=[.!?])\\s+")) {
            // Frase com número é fato ("210 hectares simulados"), mesmo que fale em dado fictício.
            if (!AVISO.matcher(f.strip()).matches() || f.matches(".*\\d.*")) {
                r.append(r.isEmpty() ? "" : " ").append(f.strip());
            }
        }
        return r.isEmpty() ? resposta : r.toString();
    }

    private static String nulo(String s) {
        return s == null ? "" : s;
    }
}
