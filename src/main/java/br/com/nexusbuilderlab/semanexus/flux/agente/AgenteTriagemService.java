package br.com.nexusbuilderlab.semanexus.flux.agente;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import br.com.nexusbuilderlab.semanexus.agentes.AgenteIndisponivelException;
import br.com.nexusbuilderlab.semanexus.agentes.AgentesConfiguracao;
import br.com.nexusbuilderlab.semanexus.agentes.AgentesController;
import br.com.nexusbuilderlab.semanexus.agentes.DefinicaoAgente;
import br.com.nexusbuilderlab.semanexus.agentes.ExecutorAgente;
import br.com.nexusbuilderlab.semanexus.agentes.FerramentaAgente;
import br.com.nexusbuilderlab.semanexus.agentes.ResultadoAgente;
import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.flux.agente.SugestaoTriagem.Duplicata;
import br.com.nexusbuilderlab.semanexus.flux.agente.SugestaoTriagem.Nucleo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DecisaoTriagem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaFiltro;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaResumo;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaFiltro;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteService;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.TipoReferencia;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Agente de Triagem (SEMA Flux, Issue #107): consulta a demanda, demandas semelhantes e os dados públicos do INPE no
 * município e sugere decisão, categoria, prioridade e núcleo regional. Só consulta (com as permissões do usuário);
 * aceitar a sugestão é feito pelo usuário no {@code POST /api/demandas/{id}/triagem}. Sem IA disponível, usa
 * {@link RegrasTriagem}. Não é transacional: cada consulta usa a transação do próprio Service.
 */
@Service
public class AgenteTriagemService {

    static final String NOME = "triagem";
    static final int MAX_SEMELHANTES = 8;
    static final String NENHUMA = "NENHUMA";

    static final String INSTRUCOES = """
            Você é o Agente de Triagem do SEMA ALERTA, um sistema DEMONSTRATIVO de demandas de fiscalização ambiental \
            de Mato Grosso (todos os dados de demandas são fictícios; os dados do INPE são públicos).
            Sua tarefa: sugerir a triagem de UMA demanda para um analista humano revisar.

            Procedimento obrigatório:
            1. Chame as ferramentas obter_demanda, buscar_demandas_semelhantes (mesmo_municipio=true) e \
            alertas_inpe_municipio (podem ser chamadas juntas).
            2. Com os resultados, responda com o JSON final.

            Categorias (use o código): DESMATAMENTO_ILEGAL, QUEIMADA_INCENDIO, RECURSOS_HIDRICOS, FAUNA_SILVESTRE, \
            PESCA_IRREGULAR, MINERACAO_IRREGULAR, RESIDUOS_POLUICAO, AREA_PRESERVACAO_PERMANENTE.
            Prioridades: BAIXA, MEDIA, ALTA, URGENTE. Queimada, desmatamento e mineração costumam ser ALTA; suba um \
            nível se houver dano em andamento, área extensa ou muitos alertas do INPE no município.
            Decisão: CLASSIFICAR quando o relato permite categoria e prioridade; SOLICITAR_COMPLEMENTACAO quando faltar \
            informação essencial (aí use categoria e prioridade NENHUMA).
            Duplicatas: só números (DEM-AAAA/NNNN) devolvidos pela ferramenta que pareçam o MESMO fato (mesmo \
            município e mesmo tipo de problema) e não finalizados. Nunca invente números.
            Justificativa: em português, 2 frases curtas (no máximo 350 caracteres), citando os fatos usados \
            (relato, semelhantes, INPE).
            Não dê orientação jurídica nem cite leis ou multas.""";

    static final Map<String, Object> ESQUEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "decisao", Map.of("type", "string", "enum", List.of("CLASSIFICAR", "SOLICITAR_COMPLEMENTACAO")),
                    "categoria", Map.of("type", "string", "enum", codigos(CategoriaFiscalizacao.values())),
                    "prioridade", Map.of("type", "string", "enum", codigos(Prioridade.values())),
                    "justificativa", Map.of("type", "string", "maxLength", 700),
                    "duplicatas", Map.of("type", "array", "maxItems", 5, "items", Map.of("type", "string"))),
            "required", List.of("decisao", "categoria", "prioridade", "justificativa", "duplicatas"),
            "additionalProperties", false);

    private final DemandaService demandas;
    private final MunicipioRepository municipios;
    private final ReferenciaSateliteService referencia;
    private final ExecutorAgente executor;
    private final AgentesConfiguracao configuracao;

    public AgenteTriagemService(DemandaService demandas, MunicipioRepository municipios,
                                ReferenciaSateliteService referencia, ExecutorAgente executor,
                                AgentesConfiguracao configuracao) {
        this.demandas = demandas;
        this.municipios = municipios;
        this.referencia = referencia;
        this.executor = executor;
        this.configuracao = configuracao;
    }

    public SugestaoTriagem sugerir(UsuarioAutenticado ator, Long id) {
        DemandaDetalhe d = demandas.detalhar(ator, id); // 404/403 conforme o escopo do usuário
        if (d.status() != StatusDemanda.PENDENTE && d.status() != StatusDemanda.EM_TRIAGEM) {
            throw new ConflitoException("A sugestão de triagem só se aplica a demandas Pendentes ou Em triagem.");
        }
        String regiao = municipios.findById(d.municipio().id()).map(Municipio::getRegiao).orElse(null);
        String[] n = RegrasTriagem.nucleo(regiao);
        Nucleo nucleo = n == null ? null : new Nucleo(n[0], n[1]);
        long[] inpe = alertasInpe(d.municipio().id());
        List<String> avisos = new ArrayList<>();

        if (executor.iaDisponivel()) {
            Map<String, DemandaResumo> vistas = new LinkedHashMap<>();
            try {
                ResultadoAgente r = executor.executar(definicao(ator, d, regiao, nucleo, inpe, vistas),
                        "Sugira a triagem da demanda " + d.numero() + " (id " + d.id() + ").", String.valueOf(id));
                return deIa(d, nucleo, inpe, r, vistas, avisos);
            } catch (AgenteIndisponivelException e) {
                avisos.add("A IA não respondeu a tempo; sugestão feita pelas regras locais.");
            }
        }
        return porRegras(ator, d, nucleo, inpe, avisos);
    }

    // ------------------------------------------------------------------------------------------- IA (agente)

    private DefinicaoAgente definicao(UsuarioAutenticado ator, DemandaDetalhe d, String regiao, Nucleo nucleo,
                                      long[] inpe, Map<String, DemandaResumo> vistas) {
        FerramentaAgente obter = new FerramentaAgente("obter_demanda",
                "Lê os dados da demanda em triagem (título, relato, município, bioma, região, origem, datas).",
                Map.of("numero", Map.of("type", "string", "description", "Número da demanda em triagem (DEM-AAAA/NNNN)")),
                List.of("numero"), e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("numero", d.numero());
                    m.put("titulo", d.titulo());
                    m.put("relato", d.descricao());
                    m.put("origem", d.origem().name());
                    m.put("municipio", d.municipio().nome());
                    m.put("bioma", d.municipio().bioma() == null ? null : d.municipio().bioma().name());
                    m.put("regiaoIntermediariaIbge", regiao);
                    m.put("nucleoRegional", nucleo == null ? null : nucleo.sigla());
                    m.put("localidade", d.localidade());
                    m.put("dataRecebimento", String.valueOf(d.dataRecebimento()));
                    m.put("status", d.status().name());
                    return new FerramentaAgente.Resultado(m, "Leu a demanda " + d.numero() + ".");
                });
        FerramentaAgente semelhantes = new FerramentaAgente("buscar_demandas_semelhantes",
                "Lista até 8 demandas (exceto a atual): do mesmo município da demanda em triagem ou, com mesmo_municipio=false, "
                        + "de qualquer município com o termo no título.",
                Map.of("termo", Map.of("type", "string", "description", "Palavra do título (opcional; vazio = nenhum)"),
                        "mesmo_municipio", Map.of("type", "boolean", "description", "Restringir ao município da demanda")),
                List.of("mesmo_municipio"), e -> {
                    boolean mesmo = !e.has("mesmo_municipio") || e.path("mesmo_municipio").asBoolean(true);
                    // No mesmo município o termo é ignorado: modelos pequenos chutam termos antes de ler o relato.
                    String termo = mesmo ? "" : e.path("termo").asText("").strip();
                    List<DemandaResumo> lista = semelhantes(ator, d, termo.isEmpty() ? null : termo,
                            mesmo ? d.municipio().id() : null, null);
                    lista.forEach(x -> vistas.put(x.numero(), x));
                    List<Map<String, Object>> dados = lista.stream().map(AgenteTriagemService::resumo).toList();
                    return new FerramentaAgente.Resultado(dados, "Buscou demandas semelhantes"
                            + (termo.isEmpty() ? "" : " com \"" + termo + "\"")
                            + (mesmo ? " em " + d.municipio().nome() : "") + ": " + lista.size() + " encontrada(s).");
                });
        FerramentaAgente alertas = new FerramentaAgente("alertas_inpe_municipio",
                "Conta focos de calor e alertas de desmatamento (DETER) do INPE no município da demanda em triagem (dados públicos).",
                Map.of("municipio", Map.of("type", "string", "description", "Município da demanda em triagem")),
                List.of("municipio"), e -> new FerramentaAgente.Resultado(
                        Map.of("municipio", d.municipio().nome(), "focosDeCalor", inpe[0],
                                "alertasDesmatamento", inpe[1], "fonte", "INPE — dados públicos"),
                        "Consultou o INPE em " + d.municipio().nome() + ": " + inpe[0] + " foco(s) de calor e "
                                + inpe[1] + " alerta(s) de desmatamento."));
        return new DefinicaoAgente(NOME, configuracao.modeloTriagem(), INSTRUCOES,
                List.of(obter, semelhantes, alertas), ESQUEMA, "low", 800, 1);
    }

    private SugestaoTriagem deIa(DemandaDetalhe d, Nucleo nucleo, long[] inpe, ResultadoAgente r,
                                 Map<String, DemandaResumo> vistas, List<String> avisos) {
        JsonNode j = r.resposta();
        DecisaoTriagem decisao = enumOu(DecisaoTriagem.class, j.path("decisao").asText(), null);
        CategoriaFiscalizacao categoria = enumOu(CategoriaFiscalizacao.class, j.path("categoria").asText(), null);
        Prioridade prioridade = enumOu(Prioridade.class, j.path("prioridade").asText(), null);
        CategoriaFiscalizacao categoriaRegra = RegrasTriagem.categoria(d.titulo(), d.descricao());
        if (decisao == null) {
            decisao = categoria != null || categoriaRegra != null ? DecisaoTriagem.CLASSIFICAR
                    : DecisaoTriagem.SOLICITAR_COMPLEMENTACAO;
            avisos.add("Decisão completada pelas regras locais.");
        }
        if (decisao == DecisaoTriagem.CLASSIFICAR) {
            if (categoria == null) {
                categoria = categoriaRegra;
                avisos.add("Categoria completada pelas regras locais.");
            }
            if (categoria == null) {
                decisao = DecisaoTriagem.SOLICITAR_COMPLEMENTACAO;
            } else if (prioridade == null) {
                prioridade = RegrasTriagem.prioridade(categoria, d.titulo(), d.descricao(), inpe[0] + inpe[1]);
                avisos.add("Prioridade completada pelas regras locais.");
            }
        }
        if (decisao == DecisaoTriagem.SOLICITAR_COMPLEMENTACAO) {
            categoria = null;
            prioridade = null;
        }
        // Só duplicatas que a ferramenta realmente devolveu (o modelo não pode inventar números).
        List<Duplicata> duplicatas = new ArrayList<>();
        for (JsonNode num : j.path("duplicatas")) {
            DemandaResumo x = vistas.get(num.asText().strip());
            if (x != null && x.status() != StatusDemanda.FINALIZADA && duplicatas.stream().noneMatch(y -> y.id().equals(x.id()))) {
                duplicatas.add(duplicata(x));
            }
        }
        String justificativa = j.path("justificativa").asText("").strip();
        if (justificativa.isEmpty()) {
            justificativa = justificativaRegras(categoria, prioridade, inpe);
        }
        justificativa = frasesCompletas(justificativa, 700);
        String modo = configuracao.provedor() == AgentesConfiguracao.Provedor.ANTHROPIC
                ? AgentesController.MODO_CLAUDE : AgentesController.MODO_LOCAL;
        return new SugestaoTriagem(modo, r.modelo(), decisao, categoria, prioridade, nucleo, justificativa,
                duplicatas, r.passos().stream().map(ResultadoAgente.PassoAgente::resumo).toList(), avisos);
    }

    /** Corta no fim da última frase completa (o modelo pode ter sido interrompido pelo limite de tamanho). */
    static String frasesCompletas(String texto, int limite) {
        String t = (texto.length() > limite ? texto.substring(0, limite) : texto).strip();
        String[] frases = t.split("(?<=[.!?])\\s+");
        if (frases.length > 3) {
            // Modelos pequenos tendem a se repetir: as 3 primeiras frases bastam para o analista.
            t = String.join(" ", Arrays.copyOf(frases, 3));
        }
        if (t.endsWith(".") || t.endsWith("!") || t.endsWith("?")) {
            return t;
        }
        int fim = t.lastIndexOf('.');
        return fim > 40 ? t.substring(0, fim + 1) : t + "…";
    }

    // ------------------------------------------------------------------------------ Regras locais (demonstrativo)

    private SugestaoTriagem porRegras(UsuarioAutenticado ator, DemandaDetalhe d, Nucleo nucleo, long[] inpe,
                                      List<String> avisos) {
        CategoriaFiscalizacao categoria = RegrasTriagem.categoria(d.titulo(), d.descricao());
        List<String> evidencias = new ArrayList<>();
        evidencias.add("Leu o relato da demanda " + d.numero() + ".");
        evidencias.add("Consultou o INPE em " + d.municipio().nome() + ": " + inpe[0] + " foco(s) de calor e "
                + inpe[1] + " alerta(s) de desmatamento.");
        if (categoria == null) {
            executor.registrarDemonstrativo(NOME, String.valueOf(d.id()), "Sugestão: solicitar complementação.");
            return new SugestaoTriagem(AgentesController.MODO_DEMONSTRATIVO, null,
                    DecisaoTriagem.SOLICITAR_COMPLEMENTACAO, null, null, nucleo,
                    "O relato não permite identificar o tipo de ocorrência; solicitar ao denunciante a descrição do "
                            + "problema ambiental e a localização.", List.of(), evidencias, avisos);
        }
        Prioridade prioridade = RegrasTriagem.prioridade(categoria, d.titulo(), d.descricao(), inpe[0] + inpe[1]);
        List<DemandaResumo> mesmas = semelhantes(ator, d, null, d.municipio().id(), categoria).stream()
                .filter(x -> x.status() != StatusDemanda.FINALIZADA).toList();
        evidencias.add("Buscou demandas da mesma categoria em " + d.municipio().nome() + ": " + mesmas.size()
                + " aberta(s).");
        executor.registrarDemonstrativo(NOME, String.valueOf(d.id()),
                "Sugestão: " + categoria.name() + ", " + prioridade.name() + ".");
        return new SugestaoTriagem(AgentesController.MODO_DEMONSTRATIVO, null, DecisaoTriagem.CLASSIFICAR,
                categoria, prioridade, nucleo, justificativaRegras(categoria, prioridade, inpe),
                mesmas.stream().limit(5).map(AgenteTriagemService::duplicata).toList(), evidencias, avisos);
    }

    private static String justificativaRegras(CategoriaFiscalizacao categoria, Prioridade prioridade, long[] inpe) {
        if (categoria == null) {
            return "Faltam informações no relato para classificar a demanda.";
        }
        return "O relato descreve ocorrência de " + categoria.rotulo().toLowerCase() + ". Prioridade "
                + prioridade.rotulo().toLowerCase() + " considerando a gravidade e os dados públicos do INPE no município ("
                + inpe[0] + " foco(s) de calor e " + inpe[1] + " alerta(s) de desmatamento).";
    }

    // ------------------------------------------------------------------------------------------------ apoio

    private List<DemandaResumo> semelhantes(UsuarioAutenticado ator, DemandaDetalhe d, String termo,
                                            Integer municipioId, CategoriaFiscalizacao categoria) {
        DemandaFiltro filtro = new DemandaFiltro(termo, null, municipioId, categoria, null, null, null, null, null, null);
        return demandas.listar(ator, filtro, PageRequest.of(0, MAX_SEMELHANTES + 1)).content().stream()
                .filter(x -> !x.id().equals(d.id()))
                .limit(MAX_SEMELHANTES)
                .collect(Collectors.toList());
    }

    private long[] alertasInpe(Integer municipioId) {
        try {
            long focos = referencia.porMunicipio(new ReferenciaFiltro(TipoReferencia.FOCO_CALOR, null, null,
                    municipioId, null)).total();
            long deter = referencia.porMunicipio(new ReferenciaFiltro(TipoReferencia.DESMATAMENTO, null, null,
                    municipioId, null)).total();
            return new long[] {focos, deter};
        } catch (RuntimeException e) {
            return new long[] {0, 0};
        }
    }

    private static Map<String, Object> resumo(DemandaResumo x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numero", x.numero());
        m.put("titulo", x.titulo());
        m.put("status", x.status().name());
        m.put("categoria", x.categoria() == null ? null : x.categoria().name());
        m.put("municipio", x.municipio().nome());
        m.put("dataRecebimento", String.valueOf(x.dataRecebimento()));
        return m;
    }

    private static Duplicata duplicata(DemandaResumo x) {
        return new Duplicata(x.id(), x.numero(), x.titulo(), x.status(), x.dataRecebimento());
    }

    static <E extends Enum<E>> E enumOu(Class<E> tipo, String valor, E padrao) {
        if (valor == null || valor.isBlank() || NENHUMA.equals(valor)) {
            return padrao;
        }
        try {
            return Enum.valueOf(tipo, valor.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return padrao;
        }
    }

    private static List<String> codigos(Enum<?>[] valores) {
        List<String> r = new ArrayList<>(Arrays.stream(valores).map(Enum::name).toList());
        r.add(NENHUMA);
        return r;
    }
}
