package br.com.nexusbuilderlab.semanexus.demo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Gera, de forma determinística (semente fixa), a massa demonstrativa de demandas. Puro: não acessa banco.
 * Datas relativas a {@code hoje} (últimos 12 meses), com sazonalidade de queimadas em julho–outubro e histórico
 * coerente com o status. AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.
 */
final class DemoMassaGerador {

    static final String MARCA = "[DEMO-SEED]";
    static final int TOTAL = 120;
    static final long SEMENTE = 18L;

    /** Municípios de MT (IBGE) usados no seed: 11 Amazônia, 10 Cerrado, 5 Pantanal. */
    static final List<Long> MUNICIPIOS_IDS = List.of(
            5100250L, 5103254L, 5101407L, 5105150L, 5106422L, 5107909L, 5107925L, 5105259L, 5103205L, 5105606L,
            5103379L,
            5103403L, 5107602L, 5107958L, 5107040L, 5106224L, 5103007L, 5108402L, 5104807L, 5103502L, 5106307L,
            5102504L, 5106505L, 5107800L, 5101605L, 5106109L);

    private static final Map<String, Integer> CONTAGEM = Map.of(
            "PENDENTE", 10, "EM_TRIAGEM", 10, "AGUARDANDO_DISTRIBUICAO", 12, "DISTRIBUIDA", 16,
            "EM_ANDAMENTO", 26, "AGUARDANDO_CONCLUSAO", 16, "FINALIZADA", 30);

    /** Idade mínima (dias) para que o histórico completo do status caiba antes de hoje. */
    private static final Map<String, Integer> IDADE_MINIMA = Map.of(
            "PENDENTE", 0, "EM_TRIAGEM", 1, "AGUARDANDO_DISTRIBUICAO", 2, "DISTRIBUIDA", 4,
            "EM_ANDAMENTO", 7, "AGUARDANDO_CONCLUSAO", 14, "FINALIZADA", 21);

    static final List<String> STATUS_ORDEM = List.of("PENDENTE", "EM_TRIAGEM", "AGUARDANDO_DISTRIBUICAO",
            "DISTRIBUIDA", "EM_ANDAMENTO", "AGUARDANDO_CONCLUSAO", "FINALIZADA");

    private static final List<String> ORIGENS = List.of("DENUNCIA", "MONITORAMENTO", "SOLICITACAO_INSTITUCIONAL",
            "ACAO_PLANEJADA", "ANALISE_AMBIENTAL");
    private static final List<String> CATEGORIAS = List.of("DESMATAMENTO_ILEGAL", "RECURSOS_HIDRICOS",
            "FAUNA_SILVESTRE", "PESCA_IRREGULAR", "MINERACAO_IRREGULAR", "RESIDUOS_POLUICAO",
            "AREA_PRESERVACAO_PERMANENTE");
    private static final List<String> PRIORIDADES = List.of("BAIXA", "MEDIA", "MEDIA", "ALTA", "ALTA", "URGENTE");

    static final Map<String, String> TITULO_BASE = Map.of(
            "DESMATAMENTO_ILEGAL", "Suspeita de desmatamento ilegal",
            "QUEIMADA_INCENDIO", "Foco de queimada em vegetação nativa",
            "RECURSOS_HIDRICOS", "Captação irregular de recursos hídricos",
            "FAUNA_SILVESTRE", "Denúncia de maus-tratos à fauna silvestre",
            "PESCA_IRREGULAR", "Pesca irregular em período de defeso",
            "MINERACAO_IRREGULAR", "Indício de garimpo irregular",
            "RESIDUOS_POLUICAO", "Descarte irregular de resíduos",
            "AREA_PRESERVACAO_PERMANENTE", "Ocupação irregular de área de preservação permanente");

    record MunicipioRef(long id, String nome, String bioma, double latitude, double longitude) {
    }

    /** Demanda do cenário fixo do roteiro (docs/DEMO.md). */
    record Cenario(String chave, String titulo, long municipioId, String categoria, String status, int idadeDias) {
    }

    static final List<Cenario> CENARIOS = List.of(
            new Cenario("C1", "Queimada em pastagem e vegetação nativa — Cáceres (cenário de demonstração)",
                    5102504L, "QUEIMADA_INCENDIO", "AGUARDANDO_CONCLUSAO", 40),
            new Cenario("C2", "Desmatamento ilegal em área de floresta — Colniza (cenário de demonstração)",
                    5103254L, "DESMATAMENTO_ILEGAL", "EM_ANDAMENTO", 25),
            new Cenario("C3", "Pesca irregular em período de defeso — Barão de Melgaço (cenário de demonstração)",
                    5101605L, "PESCA_IRREGULAR", "AGUARDANDO_DISTRIBUICAO", 6),
            new Cenario("C4", "Descarte irregular de resíduos — Sinop (cenário de demonstração)",
                    5107909L, "RESIDUOS_POLUICAO", "PENDENTE", 1),
            new Cenario("C5", "Captação irregular de recursos hídricos — Primavera do Leste (cenário de demonstração)",
                    5107040L, "RECURSOS_HIDRICOS", "FINALIZADA", 90),
            new Cenario("C6", "Foco de queimada com prazo vencido — Poconé (cenário de demonstração)",
                    5106505L, "QUEIMADA_INCENDIO", "DISTRIBUIDA", 45));

    record DemandaSeed(String chave, String titulo, String descricao, String origem, String categoria,
                       MunicipioRef municipio, String localidade, double latitude, double longitude,
                       LocalDate recebimento, String prioridade, LocalDate prazoSugerido, LocalDate prazo,
                       String status, int indice, LocalDate fiscalizacaoData, LocalDate concluidaEm) {

        boolean passou(String alvo) {
            return STATUS_ORDEM.indexOf(status) >= STATUS_ORDEM.indexOf(alvo);
        }
    }

    private DemoMassaGerador() {
    }

    /** Resultado ordenado por data de recebimento (a numeração DEM-AAAA/NNNN segue essa ordem). */
    static List<DemandaSeed> gerar(LocalDate hoje, List<MunicipioRef> municipios) {
        if (municipios.size() < 25) {
            throw new IllegalArgumentException("São necessários ao menos 25 municípios.");
        }
        Random rnd = new Random(SEMENTE);
        Map<String, Integer> restante = new HashMap<>(CONTAGEM);
        for (Cenario c : CENARIOS) {
            restante.merge(c.status(), -1, Integer::sum);
        }
        List<String> statuses = new ArrayList<>();
        for (String s : STATUS_ORDEM) {
            for (int k = 0; k < restante.get(s); k++) {
                statuses.add(s);
            }
        }
        Collections.shuffle(statuses, rnd);

        List<DemandaSeed> lista = new ArrayList<>();
        int n = municipios.size();
        for (int i = 0; i < TOTAL; i++) {
            Cenario cen = i < CENARIOS.size() ? CENARIOS.get(i) : null;
            String status = cen != null ? cen.status() : statuses.get(i - CENARIOS.size());
            int idade = cen != null ? cen.idadeDias() : sortearIdade(rnd, hoje, IDADE_MINIMA.get(status));
            LocalDate recebimento = hoje.minusDays(idade);
            MunicipioRef m = cen != null ? municipio(municipios, cen.municipioId()) : municipios.get((i * 7) % n);
            String categoria = cen != null ? cen.categoria() : categoria(rnd, recebimento);
            String origem = ORIGENS.get(rnd.nextInt(ORIGENS.size()));
            String prioridade = PRIORIDADES.get(rnd.nextInt(PRIORIDADES.size()));
            double lat = limitar(m.latitude() + (rnd.nextDouble() - 0.5) * 0.2, -18.0, -7.4);
            double lon = limitar(m.longitude() + (rnd.nextDouble() - 0.5) * 0.2, -61.6, -50.3);
            String titulo = cen != null ? cen.titulo() : TITULO_BASE.get(categoria) + " — " + m.nome();
            String descricao = "Registro fictício do seed demonstrativo: " + TITULO_BASE.get(categoria).toLowerCase()
                    + " relatado em " + m.nome() + " (bioma " + bioma(m.bioma()) + "). Dados simulados, sem relação "
                    + "com ocorrências reais. AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.";
            int pos = STATUS_ORDEM.indexOf(status);
            LocalDate prazoSugerido = pos >= 2 ? recebimento.plusDays(31) : null;
            int diasPrazo = cen != null && cen.chave().equals("C6") ? 7 : (i % 4 == 0 ? 7 : 20 + (i % 25));
            LocalDate prazo = pos >= 3 ? recebimento.plusDays(2L + diasPrazo) : null;
            lista.add(new DemandaSeed(cen != null ? cen.chave() : null, titulo, descricao, origem, categoria, m,
                    "Zona rural de " + m.nome() + " (fictícia)", lat, lon, recebimento,
                    pos >= 2 ? prioridade : null, prazoSugerido, prazo, status, i,
                    pos >= 5 ? recebimento.plusDays(7) : null, pos == 6 ? recebimento.plusDays(10) : null));
        }
        lista.sort(Comparator.comparing(DemandaSeed::recebimento).thenComparing(DemandaSeed::indice));
        return lista;
    }

    private static MunicipioRef municipio(List<MunicipioRef> ms, long id) {
        return ms.stream().filter(x -> x.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Município do cenário ausente: " + id));
    }

    /** Sorteia a idade (dias) com peso maior para jul–out, respeitando a idade mínima do status. */
    private static int sortearIdade(Random rnd, LocalDate hoje, int minima) {
        while (true) {
            int idade = minima + rnd.nextInt(364 - minima);
            int mes = hoje.minusDays(idade).getMonthValue();
            if ((mes >= 7 && mes <= 10) || rnd.nextInt(3) == 0) {
                return idade;
            }
        }
    }

    private static String categoria(Random rnd, LocalDate data) {
        int mes = data.getMonthValue();
        if (mes >= 7 && mes <= 10 && rnd.nextInt(100) < 55) {
            return "QUEIMADA_INCENDIO";
        }
        return CATEGORIAS.get(rnd.nextInt(CATEGORIAS.size()));
    }

    private static double limitar(double v, double min, double max) {
        double r = Math.max(min, Math.min(max, v));
        return Math.round(r * 1_000_000d) / 1_000_000d;
    }

    static String bioma(String codigo) {
        return switch (codigo) {
            case "AMAZONIA" -> "Amazônia";
            case "CERRADO" -> "Cerrado";
            default -> "Pantanal";
        };
    }
}
