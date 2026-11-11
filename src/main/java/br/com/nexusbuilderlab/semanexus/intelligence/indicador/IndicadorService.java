package br.com.nexusbuilderlab.semanexus.intelligence.indicador;

import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import br.com.nexusbuilderlab.semanexus.common.dto.AvisoDemonstrativo;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.flux.demanda.Demanda;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.ContagemItem;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.ContagemMunicipio;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorFiltro;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorLista;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.ResumoIndicadores;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.SerieMensalItem;
import br.com.nexusbuilderlab.semanexus.intelligence.mapa.dto.MapaResponse;
import br.com.nexusbuilderlab.semanexus.intelligence.mapa.dto.PontoMapa;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores agregados do SEMA Intelligence, calculados a cada consulta sobre as demandas do Flux (uma
 * demanda cadastrada ou finalizada altera os números na consulta seguinte). Dados simulados.
 */
@Service
@Transactional(readOnly = true)
public class IndicadorService {

    static final ZoneId FUSO = ZoneId.of("America/Cuiaba");

    private final IndicadorRepository repositorio;
    private final Clock clock;
    private final DocumentoRepository documentos;

    public IndicadorService(IndicadorRepository repositorio, Clock clock, DocumentoRepository documentos) {
        this.documentos = documentos;
        this.repositorio = repositorio;
        this.clock = clock;
    }

    /** Total de documentos ativos da biblioteca DocIA (visão institucional, sem filtros). */
    public long totalDocumentos() {
        return documentos.contarAtivos();
    }

    public ResumoIndicadores resumo(IndicadorFiltro filtro) {
        return resumir(repositorio.demandas(filtro), repositorio.contarFiscalizacoes(filtro), totalDocumentos());
    }

    /** Resumo com contagem de documentos informada (a biblioteca documental chega com a Issue #15: até lá, 0). */
    public ResumoIndicadores resumir(List<Demanda> demandas, long fiscalizacoes, long documentos) {
        LocalDate hoje = hoje();
        long finalizadas = contar(demandas, StatusDemanda.FINALIZADA);
        long atrasadas = demandas.stream().filter(d -> d.atrasada(hoje)).count();
        double mediaDias = demandas.stream()
                .filter(d -> d.getStatus() == StatusDemanda.FINALIZADA && d.getConcluidaEm() != null)
                .mapToLong(d -> Math.max(0, ChronoUnit.DAYS.between(d.getDataRecebimento(), d.getConcluidaEm().toLocalDate())))
                .average().orElse(Double.NaN);
        double percentual = demandas.isEmpty() ? 0 : Math.round(finalizadas * 1000.0 / demandas.size()) / 10.0;
        return new ResumoIndicadores(true, AvisoDemonstrativo.TEXTO, demandas.size(),
                contar(demandas, StatusDemanda.PENDENTE), contar(demandas, StatusDemanda.EM_ANDAMENTO), finalizadas,
                atrasadas, documentos, fiscalizacoes, percentual,
                Double.isNaN(mediaDias) ? null : Math.round(mediaDias * 10) / 10.0);
    }

    public IndicadorLista<SerieMensalItem> serieMensal(IndicadorFiltro filtro) {
        Map<YearMonth, long[]> meses = new LinkedHashMap<>();
        for (Demanda d : repositorio.demandas(filtro)) {
            meses.computeIfAbsent(YearMonth.from(d.getDataRecebimento()), k -> new long[2])[0]++;
            if (d.getStatus() == StatusDemanda.FINALIZADA && d.getConcluidaEm() != null) {
                meses.computeIfAbsent(YearMonth.from(d.getConcluidaEm()), k -> new long[2])[1]++;
            }
        }
        List<SerieMensalItem> itens = new ArrayList<>();
        if (!meses.isEmpty()) {
            YearMonth ini = meses.keySet().stream().min(Comparator.naturalOrder()).get();
            YearMonth fim = meses.keySet().stream().max(Comparator.naturalOrder()).get();
            for (YearMonth m = ini; !m.isAfter(fim); m = m.plusMonths(1)) {
                long[] v = meses.getOrDefault(m, new long[2]);
                itens.add(new SerieMensalItem(m.toString(), v[0], v[1]));
            }
        }
        return lista(itens);
    }

    public IndicadorLista<ContagemMunicipio> porMunicipio(IndicadorFiltro filtro) {
        Map<Integer, List<Demanda>> grupos = new LinkedHashMap<>();
        for (Demanda d : repositorio.demandas(filtro)) {
            grupos.computeIfAbsent(d.getMunicipio().getId(), k -> new ArrayList<>()).add(d);
        }
        List<ContagemMunicipio> itens = grupos.values().stream()
                .map(l -> new ContagemMunicipio(l.get(0).getMunicipio().getId(), l.get(0).getMunicipio().getNome(),
                        l.get(0).getMunicipio().getBioma(), l.size()))
                .sorted(Comparator.comparingLong(ContagemMunicipio::total).reversed()
                        .thenComparing(ContagemMunicipio::municipio))
                .toList();
        return lista(itens);
    }

    public IndicadorLista<ContagemItem> porBioma(IndicadorFiltro filtro) {
        return lista(contagemBioma(repositorio.demandas(filtro)));
    }

    public IndicadorLista<ContagemItem> porCategoria(IndicadorFiltro filtro) {
        return lista(contagemCategoria(repositorio.demandas(filtro)));
    }

    public IndicadorLista<ContagemItem> porSituacao(IndicadorFiltro filtro) {
        return lista(contagemSituacao(repositorio.demandas(filtro)));
    }

    public MapaResponse mapa(IndicadorFiltro filtro) {
        LocalDate hoje = hoje();
        List<PontoMapa> pontos = repositorio.demandas(filtro).stream().map(d -> {
            boolean propria = d.getLatitude() != null && d.getLongitude() != null;
            return new PontoMapa(d.getId(), d.getNumero(), d.getTitulo(), d.getMunicipio().getId(),
                    d.getMunicipio().getNome(),
                    propria ? d.getLatitude() : d.getMunicipio().getLatitude(),
                    propria ? d.getLongitude() : d.getMunicipio().getLongitude(),
                    !propria, d.getStatus(), d.getCategoria(), d.getPrioridade(), d.atrasada(hoje));
        }).toList();
        return new MapaResponse(true, AvisoDemonstrativo.TEXTO, pontos);
    }

    // ---- reutilizados pelo dashboard

    public List<Demanda> demandasDoFiltro(IndicadorFiltro filtro) {
        return repositorio.demandas(filtro);
    }

    public long fiscalizacoes(IndicadorFiltro filtro) {
        return repositorio.contarFiscalizacoes(filtro);
    }

    public static List<ContagemItem> contagemSituacao(List<Demanda> demandas) {
        Map<StatusDemanda, Long> m = new EnumMap<>(StatusDemanda.class);
        for (StatusDemanda s : StatusDemanda.values()) {
            m.put(s, 0L);
        }
        demandas.forEach(d -> m.merge(d.getStatus(), 1L, Long::sum));
        return m.entrySet().stream()
                .map(e -> new ContagemItem(e.getKey().name(), e.getKey().rotulo(), e.getValue())).toList();
    }

    public static List<ContagemItem> contagemBioma(List<Demanda> demandas) {
        Map<Bioma, Long> m = new EnumMap<>(Bioma.class);
        for (Bioma b : Bioma.values()) {
            m.put(b, 0L);
        }
        demandas.forEach(d -> m.merge(d.getMunicipio().getBioma(), 1L, Long::sum));
        return m.entrySet().stream()
                .map(e -> new ContagemItem(e.getKey().name(), e.getKey().rotulo(), e.getValue())).toList();
    }

    public static List<ContagemItem> contagemCategoria(List<Demanda> demandas) {
        Map<CategoriaFiscalizacao, Long> m = new EnumMap<>(CategoriaFiscalizacao.class);
        for (CategoriaFiscalizacao c : CategoriaFiscalizacao.values()) {
            m.put(c, 0L);
        }
        demandas.stream().filter(d -> d.getCategoria() != null).forEach(d -> m.merge(d.getCategoria(), 1L, Long::sum));
        return m.entrySet().stream()
                .map(e -> new ContagemItem(e.getKey().name(), e.getKey().rotulo(), e.getValue())).toList();
    }

    private static long contar(List<Demanda> demandas, StatusDemanda status) {
        return demandas.stream().filter(d -> d.getStatus() == status).count();
    }

    private static <T> IndicadorLista<T> lista(List<T> itens) {
        return new IndicadorLista<>(true, AvisoDemonstrativo.TEXTO, itens);
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(FUSO));
    }
}
