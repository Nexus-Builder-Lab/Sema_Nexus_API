package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.GlobalExceptionHandler.ErroCampo;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ChecklistModelo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ChecklistModelo.Item;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ChecklistModelo.TipoItem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ChecklistPreenchido;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modelos de checklist estruturado por {@link CategoriaFiscalizacao} (Issue #145), versionados no código, e a
 * validação do checklist enviado no registro da fiscalização: item desconhecido é recusado, obrigatório ausente
 * é recusado e cada resposta precisa ter o tipo do item. Itens fictícios/demonstrativos: não substituem roteiros
 * oficiais de fiscalização. Mudou um modelo de forma incompatível → incremente {@link #VERSAO}.
 */
public final class ChecklistFiscalizacao {

    /** Versão dos modelos abaixo; gravada no JSON de cada fiscalização. */
    public static final int VERSAO = 1;
    static final int LIMITE_TEXTO = 500;
    static final BigDecimal LIMITE_NUMERO = new BigDecimal("1000000000");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<CategoriaFiscalizacao, List<Item>> MODELOS = new EnumMap<>(CategoriaFiscalizacao.class);

    static {
        Item acessivel = bool("local_acessivel", "O local estava acessível para a vistoria?", true);
        Item obs = texto("observacoes_checklist", "Observações do checklist", false);
        MODELOS.put(CategoriaFiscalizacao.DESMATAMENTO_ILEGAL, List.of(acessivel,
                bool("supressao_confirmada", "A supressão de vegetação foi confirmada?", true),
                numero("area_estimada_ha", "Área suprimida estimada (ha)", true),
                bool("vegetacao_nativa", "A vegetação suprimida era nativa?", false),
                bool("maquinario_no_local", "Havia maquinário no local?", true), obs));
        MODELOS.put(CategoriaFiscalizacao.QUEIMADA_INCENDIO, List.of(acessivel,
                bool("foco_ativo", "Havia foco ativo no momento da vistoria?", true),
                numero("area_atingida_ha", "Área atingida estimada (ha)", true),
                bool("origem_identificada", "A origem do fogo foi identificada?", false), obs));
        MODELOS.put(CategoriaFiscalizacao.RECURSOS_HIDRICOS, List.of(acessivel,
                bool("captacao_irregular", "Há captação de água sem outorga aparente?", true),
                bool("lancamento_efluente", "Há lançamento de efluente no corpo hídrico?", true),
                texto("corpo_hidrico", "Corpo hídrico afetado", true), obs));
        MODELOS.put(CategoriaFiscalizacao.FAUNA_SILVESTRE, List.of(acessivel,
                bool("animais_encontrados", "Foram encontrados animais silvestres?", true),
                numero("quantidade_animais", "Quantidade de animais", false),
                texto("especies_observadas", "Espécies observadas", false), obs));
        MODELOS.put(CategoriaFiscalizacao.PESCA_IRREGULAR, List.of(acessivel,
                bool("petrechos_proibidos", "Havia petrechos proibidos?", true),
                bool("periodo_defeso", "A atividade ocorreu no período de defeso?", true),
                numero("pescado_kg", "Pescado encontrado (kg)", false), obs));
        MODELOS.put(CategoriaFiscalizacao.MINERACAO_IRREGULAR, List.of(acessivel,
                bool("atividade_em_operacao", "A atividade estava em operação?", true),
                numero("area_degradada_ha", "Área degradada estimada (ha)", true),
                bool("indicio_mercurio", "Há indício de uso de mercúrio?", false), obs));
        MODELOS.put(CategoriaFiscalizacao.RESIDUOS_POLUICAO, List.of(acessivel,
                bool("residuos_expostos", "Havia resíduos expostos?", true),
                texto("tipo_residuo", "Tipo de resíduo", true),
                numero("volume_estimado_m3", "Volume estimado (m³)", false), obs));
        MODELOS.put(CategoriaFiscalizacao.AREA_PRESERVACAO_PERMANENTE, List.of(acessivel,
                bool("intervencao_app", "Houve intervenção na APP?", true),
                numero("faixa_afetada_m", "Largura da faixa afetada (m)", false),
                bool("edificacao_no_local", "Há edificação no local?", false), obs));
        if (MODELOS.size() != CategoriaFiscalizacao.values().length) {
            throw new IllegalStateException("Toda categoria de fiscalização precisa de um modelo de checklist.");
        }
    }

    private ChecklistFiscalizacao() {
    }

    public static ChecklistModelo modelo(CategoriaFiscalizacao categoria) {
        return new ChecklistModelo(categoria, categoria.rotulo(), VERSAO, MODELOS.get(categoria));
    }

    public static List<ChecklistModelo> modelos() {
        return Arrays.stream(CategoriaFiscalizacao.values()).map(ChecklistFiscalizacao::modelo).toList();
    }

    /**
     * Valida as respostas contra o modelo da categoria e devolve o JSON a gravar ({@code null} se não houver
     * checklist). Erros por campo ({@code checklist.<id>}) em um único 400.
     */
    public static String validarEmJson(CategoriaFiscalizacao categoria, Map<String, Object> respostas) {
        if (respostas == null) {
            return null;
        }
        if (categoria == null) {
            throw new DadosInvalidosException("checklist", "A demanda não tem categoria para validar o checklist.");
        }
        if (respostas.size() > 50) {
            throw new DadosInvalidosException("checklist", "Checklist com itens demais.");
        }
        List<Item> itens = MODELOS.get(categoria);
        Map<String, Item> porId = new LinkedHashMap<>();
        itens.forEach(i -> porId.put(i.id(), i));
        List<ErroCampo> erros = new ArrayList<>();
        for (String chave : respostas.keySet()) {
            if (!porId.containsKey(chave)) {
                erros.add(new ErroCampo("checklist." + limitarChave(chave),
                        "Item desconhecido para a categoria " + categoria.rotulo() + "."));
            }
        }
        Map<String, Object> normalizado = new LinkedHashMap<>();
        for (Item item : itens) {
            Object valor = respostas.get(item.id());
            if (valor instanceof String s && s.isBlank()) {
                valor = null;
            }
            if (valor == null) {
                if (item.obrigatorio()) {
                    erros.add(new ErroCampo("checklist." + item.id(), "Item obrigatório: " + item.rotulo()));
                }
                continue;
            }
            Object convertido = converter(item, valor);
            if (convertido == null) {
                erros.add(new ErroCampo("checklist." + item.id(), mensagemTipo(item)));
            } else {
                normalizado.put(item.id(), convertido);
            }
        }
        if (!erros.isEmpty()) {
            throw new DadosInvalidosException(erros);
        }
        Map<String, Object> documento = new LinkedHashMap<>();
        documento.put("versao", VERSAO);
        documento.put("categoria", categoria.name());
        documento.put("itens", normalizado);
        try {
            return JSON.writeValueAsString(documento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o checklist.", e);
        }
    }

    /** Lê o JSON gravado; {@code null} se vazio ou ilegível (nunca derruba o detalhe da demanda). */
    public static ChecklistPreenchido ler(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> doc = JSON.readValue(json, new TypeReference<Map<String, Object>>() { });
            int versao = doc.get("versao") instanceof Number n ? n.intValue() : VERSAO;
            CategoriaFiscalizacao categoria = CategoriaFiscalizacao.valueOf(String.valueOf(doc.get("categoria")));
            @SuppressWarnings("unchecked")
            Map<String, Object> itens = doc.get("itens") instanceof Map<?, ?> m
                    ? new LinkedHashMap<>((Map<String, Object>) m) : Map.of();
            return new ChecklistPreenchido(versao, categoria, itens);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Object converter(Item item, Object valor) {
        return switch (item.tipo()) {
            case BOOLEANO -> valor instanceof Boolean b ? b : null;
            case TEXTO -> valor instanceof String s && s.strip().length() <= LIMITE_TEXTO ? s.strip() : null;
            case NUMERO -> numero(valor);
        };
    }

    private static BigDecimal numero(Object valor) {
        if (!(valor instanceof Number n) || valor instanceof Double d && (d.isNaN() || d.isInfinite())
                || valor instanceof Float f && (f.isNaN() || f.isInfinite())) {
            return null;
        }
        BigDecimal b = new BigDecimal(n.toString());
        return b.signum() < 0 || b.compareTo(LIMITE_NUMERO) > 0 ? null : b;
    }

    private static String mensagemTipo(Item item) {
        return switch (item.tipo()) {
            case BOOLEANO -> "Responda verdadeiro ou falso.";
            case TEXTO -> "Informe um texto de até " + LIMITE_TEXTO + " caracteres.";
            case NUMERO -> "Informe um número entre 0 e " + LIMITE_NUMERO.toPlainString() + ".";
        };
    }

    private static String limitarChave(String chave) {
        String limpa = chave.replaceAll("[^A-Za-z0-9_]", "");
        return limpa.length() > 60 ? limpa.substring(0, 60) : limpa;
    }

    private static Item bool(String id, String rotulo, boolean obrigatorio) {
        return new Item(id, rotulo, TipoItem.BOOLEANO, obrigatorio);
    }

    private static Item texto(String id, String rotulo, boolean obrigatorio) {
        return new Item(id, rotulo, TipoItem.TEXTO, obrigatorio);
    }

    private static Item numero(String id, String rotulo, boolean obrigatorio) {
        return new Item(id, rotulo, TipoItem.NUMERO, obrigatorio);
    }
}
