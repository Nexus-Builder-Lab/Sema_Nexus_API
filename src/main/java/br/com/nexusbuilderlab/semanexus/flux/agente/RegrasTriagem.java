package br.com.nexusbuilderlab.semanexus.flux.agente;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoSpecification;

/**
 * Regras locais do Agente de Triagem (modo demonstrativo, sem IA generativa): palavras-chave → categoria, gravidade
 * e focos do INPE → prioridade, região intermediária do IBGE → núcleo regional fictício.
 */
final class RegrasTriagem {

    /** Núcleo Regional de Fiscalização (fictício) por região intermediária do IBGE ({@code MUNICIPIO.REGIAO}). */
    static final Map<String, String[]> NUCLEOS = Map.of(
            "Cuiabá", new String[] {"NR-CBA", "Núcleo Regional de Cuiabá"},
            "Cáceres", new String[] {"NR-CAC", "Núcleo Regional de Cáceres"},
            "Rondonópolis", new String[] {"NR-ROO", "Núcleo Regional de Rondonópolis"},
            "Barra do Garças", new String[] {"NR-BAG", "Núcleo Regional de Barra do Garças"},
            "Sinop", new String[] {"NR-SNO", "Núcleo Regional de Sinop"});

    /** Ordem importa: a primeira categoria com mais termos encontrados vence. Termos já normalizados (sem acento). */
    private static final Map<CategoriaFiscalizacao, List<String>> TERMOS = new LinkedHashMap<>();

    static {
        TERMOS.put(CategoriaFiscalizacao.QUEIMADA_INCENDIO, List.of("queimada", "incendio", "fogo", "foco de calor",
                "focos de calor", "queima"));
        TERMOS.put(CategoriaFiscalizacao.DESMATAMENTO_ILEGAL, List.of("desmat", "supressao", "derrubada",
                "motosserra", "corte raso", "abertura de area"));
        TERMOS.put(CategoriaFiscalizacao.PESCA_IRREGULAR, List.of("pesca", "peixe", "defeso", "pescado"));
        TERMOS.put(CategoriaFiscalizacao.MINERACAO_IRREGULAR, List.of("garimpo", "minera", "draga", "extracao de "
                + "areia", "cascalho"));
        TERMOS.put(CategoriaFiscalizacao.FAUNA_SILVESTRE, List.of("fauna", "animal", "animais", "caca", "silvestre",
                "passaro", "ave"));
        TERMOS.put(CategoriaFiscalizacao.RECURSOS_HIDRICOS, List.of("captacao", "hidric", "nascente", "outorga",
                "poco", "barramento", "represa", "agua"));
        TERMOS.put(CategoriaFiscalizacao.RESIDUOS_POLUICAO, List.of("residuo", "lixo", "descarte", "esgoto",
                "poluic", "efluente", "oleo", "contamina"));
        TERMOS.put(CategoriaFiscalizacao.AREA_PRESERVACAO_PERMANENTE, List.of("preservacao permanente", " app ",
                "mata ciliar", "margem do rio", "vereda"));
    }

    private static final List<String> AGRAVANTES = List.of("urgente", "em andamento", "neste momento", "agora",
            "grande", "extensa", "hectares", "risco", "reincid");

    /** Focos + alertas DETER no raio de 10 km, nos 90 dias anteriores ao recebimento, a partir dos quais a prioridade sobe um nível. */
    static final long LIMIAR_RAIO = 10;

    private RegrasTriagem() {
    }

    static String normalizar(String texto) {
        return " " + DocumentoSpecification.normalizar(texto == null ? "" : texto) + " ";
    }

    /** Categoria com mais termos no título/descrição; nulo se nenhum termo aparece. */
    static CategoriaFiscalizacao categoria(String titulo, String descricao) {
        String t = normalizar(titulo + " " + descricao);
        CategoriaFiscalizacao melhor = null;
        int max = 0;
        for (var e : TERMOS.entrySet()) {
            int n = 0;
            for (String termo : e.getValue()) {
                if (t.contains(termo)) {
                    n++;
                }
            }
            if (n > max) {
                max = n;
                melhor = e.getKey();
            }
        }
        return melhor;
    }

    /** Base pela categoria; sobe um nível com agravante no texto ou muitos focos/alertas do INPE no município. */
    static Prioridade prioridade(CategoriaFiscalizacao categoria, String titulo, String descricao, long alertasInpe) {
        return prioridade(categoria, titulo, descricao, alertasInpe, false);
    }

    /** Com {@code espacial}, a contagem é a do raio em torno da coordenada (limiar menor: 5) e não a do município (50). */
    static Prioridade prioridade(CategoriaFiscalizacao categoria, String titulo, String descricao, long alertasInpe,
                                 boolean espacial) {
        return prioridade(categoria, titulo, descricao, alertasInpe, espacial, false);
    }

    /** Com {@code sobreposicaoSensivel} (dentro de UC ou Terra Indígena pública), a prioridade sobe um nível. */
    static Prioridade prioridade(CategoriaFiscalizacao categoria, String titulo, String descricao, long alertasInpe,
                                 boolean espacial, boolean sobreposicaoSensivel) {
        Prioridade base = switch (categoria) {
            case QUEIMADA_INCENDIO, DESMATAMENTO_ILEGAL, MINERACAO_IRREGULAR -> Prioridade.ALTA;
            case null -> Prioridade.MEDIA;
            default -> Prioridade.MEDIA;
        };
        String t = normalizar(titulo + " " + descricao);
        boolean agravante = sobreposicaoSensivel
                || AGRAVANTES.stream().anyMatch(t::contains)
                || alertasInpe >= (espacial ? LIMIAR_RAIO : 50);
        if (!agravante) {
            return base;
        }
        return switch (base) {
            case BAIXA -> Prioridade.MEDIA;
            case MEDIA -> Prioridade.ALTA;
            default -> Prioridade.URGENTE;
        };
    }

    /** Sigla e nome do núcleo da região; nulo se a região não tem núcleo. */
    static String[] nucleo(String regiao) {
        return regiao == null ? null : NUCLEOS.get(regiao);
    }
}
