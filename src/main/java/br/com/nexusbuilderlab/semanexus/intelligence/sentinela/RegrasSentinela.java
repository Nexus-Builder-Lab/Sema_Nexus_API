package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.BriefingSentinela.AcaoSugerida;
import br.com.nexusbuilderlab.semanexus.intelligence.sentinela.BriefingSentinela.Anomalia;

/**
 * Regras determinísticas da Sentinela Territorial (Issue #138). Funções puras sobre registros simples (testáveis com
 * fixtures, sem banco): (a) municípios com concentração de focos/DETER na janela recente do INPE e sem demanda aberta;
 * (b) filas acumuladas — demandas paradas no mesmo status há {@code filaDias} dias ou mais; (c) equipes sobrecarregadas
 * — demandas ativas ou atrasadas por fiscal acima dos limites.
 */
public final class RegrasSentinela {

    public static final String INPE_SEM_DEMANDA = "INPE_SEM_DEMANDA";
    public static final String FILA_ACUMULADA = "FILA_ACUMULADA";
    public static final String EQUIPE_SOBRECARREGADA = "EQUIPE_SOBRECARREGADA";
    public static final String ALTA = "ALTA";
    public static final String MEDIA = "MEDIA";
    public static final String BAIXA = "BAIXA";
    /** Status em que a demanda espera uma ação de alguém (fila). */
    public static final List<StatusDemanda> STATUS_FILA = List.of(StatusDemanda.PENDENTE, StatusDemanda.EM_TRIAGEM,
            StatusDemanda.AGUARDANDO_DISTRIBUICAO, StatusDemanda.AGUARDANDO_CONCLUSAO);
    /** Tamanho da célula (graus, ~11 km) usada para achar o agrupamento mais denso de registros do INPE. */
    static final double CELULA_GRAUS = 0.1;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private RegrasSentinela() {
    }

    /** Focos e alertas DETER de um município na janela recente. */
    public record ContagemInpe(int municipioId, String municipio, long focos, long deter) {
        public long total() {
            return focos + deter;
        }
    }

    /** Centro do agrupamento mais denso (célula de 0,1°) e quantos registros ele tem. */
    public record Agrupamento(double latitude, double longitude, long registros) {
    }

    /** Demanda numa fila e desde quando está no status atual. */
    public record ItemFila(long id, String numero, StatusDemanda status, LocalDateTime desde) {
    }

    /** Carga de uma equipe ativa: fiscais elegíveis, demandas ativas e atrasadas. */
    public record CargaEquipe(long equipeId, String equipe, String nucleo, int fiscais, long ativas, long atrasadas) {
    }

    // -------------------------------------------------------------------------------------- (a) INPE sem demanda

    /**
     * Municípios com {@code minRegistrosInpe} ou mais focos + DETER na janela e nenhuma demanda aberta, do maior para o
     * menor total (desempate pelo nome), limitados a {@code maxPorTipo}. Severidade: ≥ 3× o mínimo ALTA, ≥ 2× MEDIA.
     */
    public static List<Anomalia> inpeSemDemanda(List<ContagemInpe> contagens, Map<Integer, Long> abertasPorMunicipio,
                                                ParametrosSentinela p, LocalDate de, LocalDate ate,
                                                Function<Integer, Agrupamento> agrupamento) {
        List<ContagemInpe> candidatos = contagens.stream()
                .filter(c -> c.total() >= p.minRegistrosInpe())
                .filter(c -> abertasPorMunicipio.getOrDefault(c.municipioId(), 0L) == 0)
                .sorted(Comparator.comparingLong(ContagemInpe::total).reversed()
                        .thenComparing(ContagemInpe::municipio))
                .limit(p.maxPorTipo())
                .toList();
        List<Anomalia> r = new ArrayList<>();
        for (ContagemInpe c : candidatos) {
            Agrupamento a = agrupamento.apply(c.municipioId());
            Map<String, Object> numeros = new LinkedHashMap<>();
            numeros.put("focos", c.focos());
            numeros.put("deter", c.deter());
            numeros.put("total", c.total());
            numeros.put("demandasAbertas", 0);
            numeros.put("janelaDias", p.janelaInpeDias());
            if (a != null) {
                numeros.put("registrosNoAgrupamento", a.registros());
            }
            String descricao = c.municipio() + " teve " + c.focos() + " foco(s) de calor e " + c.deter()
                    + " alerta(s) DETER nos últimos " + p.janelaInpeDias() + " dias do pacote do INPE e não tem "
                    + "demanda aberta." + (a == null ? "" : " O agrupamento mais denso reúne " + a.registros()
                    + " registro(s).");
            List<AcaoSugerida> acoes = new ArrayList<>();
            Map<String, Object> mapa = new LinkedHashMap<>();
            mapa.put("municipioId", c.municipioId());
            mapa.put("camada", "INPE");
            mapa.put("de", de == null ? null : de.toString());
            mapa.put("ate", ate == null ? null : ate.toString());
            acoes.add(new AcaoSugerida("FILTRAR_MAPA", "Ver focos e DETER de " + c.municipio() + " no mapa", mapa));
            Map<String, Object> cadastro = new LinkedHashMap<>();
            cadastro.put("origem", Origem.MONITORAMENTO.name());
            cadastro.put("municipioId", c.municipioId());
            cadastro.put("municipio", c.municipio());
            cadastro.put("categoria", (c.deter() > c.focos() ? CategoriaFiscalizacao.DESMATAMENTO_ILEGAL
                    : CategoriaFiscalizacao.QUEIMADA_INCENDIO).name());
            cadastro.put("latitude", a == null ? null : a.latitude());
            cadastro.put("longitude", a == null ? null : a.longitude());
            cadastro.put("titulo", "Monitoramento INPE — " + c.municipio());
            acoes.add(new AcaoSugerida("CRIAR_DEMANDA_MONITORAMENTO",
                    "Abrir demanda de monitoramento em " + c.municipio(), cadastro));
            r.add(new Anomalia(INPE_SEM_DEMANDA + ":" + c.municipioId(), INPE_SEM_DEMANDA,
                    severidade(c.total(), p.minRegistrosInpe()),
                    c.municipio() + ": " + c.total() + " registro(s) do INPE sem demanda aberta", descricao, numeros,
                    acoes, c.municipio()));
        }
        return r;
    }

    /**
     * Agrupamento mais denso: células de {@link #CELULA_GRAUS}; vence a com mais pontos (desempate pela menor
     * latitude e longitude da célula); o centro é a média dos pontos da célula (5 casas). Nulo sem pontos.
     */
    public static Agrupamento agrupamentoMaisDenso(List<double[]> pontos) {
        if (pontos == null || pontos.isEmpty()) {
            return null;
        }
        Map<String, List<double[]>> celulas = new LinkedHashMap<>();
        for (double[] p : pontos) {
            long la = (long) Math.floor(p[0] / CELULA_GRAUS);
            long lo = (long) Math.floor(p[1] / CELULA_GRAUS);
            celulas.computeIfAbsent(la + ":" + lo, k -> new ArrayList<>()).add(p);
        }
        List<double[]> melhor = celulas.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<double[]>>>comparingInt(e -> e.getValue().size()).reversed()
                        .thenComparingLong(e -> Long.parseLong(e.getKey().split(":")[0]))
                        .thenComparingLong(e -> Long.parseLong(e.getKey().split(":")[1])))
                .findFirst().orElseThrow().getValue();
        double lat = melhor.stream().mapToDouble(x -> x[0]).average().orElse(0);
        double lon = melhor.stream().mapToDouble(x -> x[1]).average().orElse(0);
        return new Agrupamento(arredondar(lat, 5), arredondar(lon, 5), melhor.size());
    }

    // -------------------------------------------------------------------------------------- (b) filas acumuladas

    /**
     * Para cada status de {@link #STATUS_FILA}: demandas paradas há {@code filaDias} dias ou mais; com
     * {@code filaMinimo} ou mais vira anomalia. Severidade: ≥ 3× o mínimo ALTA, ≥ 2× MEDIA, senão BAIXA.
     */
    public static List<Anomalia> filasAcumuladas(List<ItemFila> itens, LocalDate hoje, ParametrosSentinela p) {
        List<Anomalia> r = new ArrayList<>();
        for (StatusDemanda s : STATUS_FILA) {
            List<ItemFila> doStatus = itens.stream().filter(i -> i.status() == s).toList();
            List<ItemFila> paradas = doStatus.stream()
                    .filter(i -> dias(i.desde(), hoje) >= p.filaDias())
                    .sorted(Comparator.comparing(ItemFila::desde).thenComparingLong(ItemFila::id))
                    .toList();
            if (paradas.size() < p.filaMinimo()) {
                continue;
            }
            ItemFila antiga = paradas.get(0);
            long maisAntiga = dias(antiga.desde(), hoje);
            Map<String, Object> numeros = new LinkedHashMap<>();
            numeros.put("noStatus", doStatus.size());
            numeros.put("paradas", paradas.size());
            numeros.put("filaDias", p.filaDias());
            numeros.put("diasMaisAntiga", maisAntiga);
            numeros.put("maisAntiga", antiga.numero());
            String rotulo = s.rotulo().toLowerCase(PT_BR);
            String descricao = paradas.size() + " de " + doStatus.size() + " demanda(s) em \"" + rotulo
                    + "\" estão paradas há " + p.filaDias() + " dias ou mais; a mais antiga (" + antiga.numero()
                    + ") está há " + maisAntiga + " dias.";
            List<AcaoSugerida> acoes = List.of(
                    new AcaoSugerida("FILTRAR_DEMANDAS", "Ver demandas em \"" + rotulo + "\"",
                            new LinkedHashMap<>(Map.of("status", s.name()))),
                    new AcaoSugerida("FILTRAR_MAPA", "Ver no mapa as demandas em \"" + rotulo + "\"",
                            new LinkedHashMap<>(Map.of("status", s.name()))));
            r.add(new Anomalia(FILA_ACUMULADA + ":" + s.name(), FILA_ACUMULADA,
                    severidade(paradas.size(), p.filaMinimo()),
                    "Fila acumulada em \"" + rotulo + "\": " + paradas.size() + " parada(s) há " + p.filaDias()
                            + "+ dias", descricao, numeros, acoes, rotulo));
        }
        return r;
    }

    // ---------------------------------------------------------------------------------- (c) equipes sobrecarregadas

    /**
     * Equipes com mais demandas ativas por fiscal que {@code ativasPorFiscal} ou mais atrasadas por fiscal que
     * {@code atrasadasPorFiscal}. Os dois limites estourados: ALTA; um: MEDIA. Ordem: severidade e atrasadas/fiscal.
     */
    public static List<Anomalia> equipesSobrecarregadas(List<CargaEquipe> cargas, ParametrosSentinela p) {
        record Avaliada(CargaEquipe c, double ativas, double atrasadas, boolean estourouAtivas,
                        boolean estourouAtrasadas) {
        }
        return cargas.stream()
                .filter(c -> c.fiscais() > 0)
                .map(c -> {
                    double a = arredondar((double) c.ativas() / c.fiscais(), 1);
                    double t = arredondar((double) c.atrasadas() / c.fiscais(), 1);
                    return new Avaliada(c, a, t, a > p.ativasPorFiscal(), t > p.atrasadasPorFiscal());
                })
                .filter(x -> x.estourouAtivas() || x.estourouAtrasadas())
                .sorted(Comparator.<Avaliada>comparingInt(x -> x.estourouAtivas() && x.estourouAtrasadas() ? 0 : 1)
                        .thenComparing(Comparator.comparingDouble(Avaliada::atrasadas).reversed())
                        .thenComparing(Comparator.comparingDouble(Avaliada::ativas).reversed())
                        .thenComparingLong(x -> x.c().equipeId()))
                .limit(p.maxPorTipo())
                .map(x -> {
                    CargaEquipe c = x.c();
                    Map<String, Object> numeros = new LinkedHashMap<>();
                    numeros.put("fiscais", c.fiscais());
                    numeros.put("ativas", c.ativas());
                    numeros.put("atrasadas", c.atrasadas());
                    numeros.put("ativasPorFiscal", x.ativas());
                    numeros.put("atrasadasPorFiscal", x.atrasadas());
                    numeros.put("limiteAtivasPorFiscal", p.ativasPorFiscal());
                    numeros.put("limiteAtrasadasPorFiscal", p.atrasadasPorFiscal());
                    String nome = c.equipe() + (c.nucleo() == null ? "" : " (" + c.nucleo() + ")");
                    String descricao = nome + " tem " + c.ativas() + " demanda(s) ativa(s) e " + c.atrasadas()
                            + " atrasada(s) para " + c.fiscais() + " fiscal(is): " + decimal(x.ativas())
                            + " ativa(s) e " + decimal(x.atrasadas()) + " atrasada(s) por fiscal (limites "
                            + decimal(p.ativasPorFiscal()) + " e " + decimal(p.atrasadasPorFiscal()) + ").";
                    Map<String, Object> filtro = new LinkedHashMap<>();
                    filtro.put("equipeId", c.equipeId());
                    Map<String, Object> atrasadas = new LinkedHashMap<>(filtro);
                    atrasadas.put("atrasada", true);
                    List<AcaoSugerida> acoes = List.of(
                            new AcaoSugerida("FILTRAR_DEMANDAS", "Ver demandas atrasadas da " + c.equipe(), atrasadas),
                            new AcaoSugerida("FILTRAR_DEMANDAS", "Ver demandas da " + c.equipe(), filtro));
                    return new Anomalia(EQUIPE_SOBRECARREGADA + ":" + c.equipeId(), EQUIPE_SOBRECARREGADA,
                            x.estourouAtivas() && x.estourouAtrasadas() ? ALTA : MEDIA,
                            nome + " sobrecarregada", descricao, numeros, acoes, c.equipe());
                })
                .toList();
    }

    // ------------------------------------------------------------------------------------------------ apoio

    /** Ordena por severidade (ALTA, MEDIA, BAIXA), mantendo a ordem de cada regra. */
    public static List<Anomalia> ordenar(List<Anomalia> anomalias) {
        return anomalias.stream().sorted(Comparator.comparingInt(a -> peso(a.severidade()))).toList();
    }

    static String severidade(long valor, long minimo) {
        if (valor >= 3 * minimo) {
            return ALTA;
        }
        return valor >= 2 * minimo ? MEDIA : BAIXA;
    }

    private static int peso(String severidade) {
        return switch (severidade) {
            case ALTA -> 0;
            case MEDIA -> 1;
            default -> 2;
        };
    }

    static long dias(LocalDateTime desde, LocalDate hoje) {
        return desde == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(desde.toLocalDate(), hoje));
    }

    static double arredondar(double v, int casas) {
        double f = Math.pow(10, casas);
        return Math.round(v * f) / f;
    }

    static String decimal(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(PT_BR, "%.1f", v);
    }
}
