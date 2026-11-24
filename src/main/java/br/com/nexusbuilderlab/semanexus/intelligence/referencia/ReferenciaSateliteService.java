package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.ContagemMunicipio;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.Importacao;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.Metadados;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.Ponto;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.PontosResponse;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.PorMunicipioResponse;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.common.geo.GeoJson;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.PontoDetalhe;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteRepository.Registro;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta somente leitura da camada de referência (dados públicos do INPE), isolada dos indicadores do Flux. */
@Service
@Transactional(readOnly = true)
public class ReferenciaSateliteService {

    public static final int LIMITE_PADRAO = 5000;
    public static final int LIMITE_MAXIMO = 20000;

    private final ReferenciaSateliteRepository repository;

    public ReferenciaSateliteService(ReferenciaSateliteRepository repository) {
        this.repository = repository;
    }

    /** Pontos mais próximos do centro na caixa e no período (pré-filtro espacial; refinamento é do chamador). */
    public java.util.List<ReferenciaSateliteRepository.PontoEspacial> maisProximosNaCaixa(TipoReferencia tipo,
            double latMin, double latMax, double lonMin, double lonMax, java.time.LocalDateTime de,
            java.time.LocalDateTime ate, double latCentro, double lonCentro, int limite) {
        return repository.maisProximosNaCaixa(tipo, latMin, latMax, lonMin, lonMax, de, ate, latCentro, lonCentro,
                limite);
    }

    public String geometria(long id) {
        return repository.geometria(id);
    }

    public Metadados metadados() {
        List<Importacao> imp = repository.importacoes();
        return new Metadados(ReferenciaDto.FONTE, false, atualizadoEm(imp), ReferenciaDto.AVISO, imp);
    }

    public PontosResponse pontos(ReferenciaFiltro filtro, Integer limite) {
        validar(filtro);
        int efetivo = limite == null ? LIMITE_PADRAO : Math.max(1, Math.min(limite, LIMITE_MAXIMO));
        List<Ponto> lidos = repository.pontos(filtro, efetivo + 1);
        boolean truncado = lidos.size() > efetivo;
        List<Ponto> pontos = truncado ? lidos.subList(0, efetivo) : lidos;
        return new PontosResponse(ReferenciaDto.FONTE, false, atualizadoEm(repository.importacoes()),
                ReferenciaDto.AVISO, efetivo, truncado, pontos);
    }

    public PorMunicipioResponse porMunicipio(ReferenciaFiltro filtro) {
        validar(filtro);
        List<ContagemMunicipio> itens = repository.porMunicipio(filtro);
        long total = itens.stream().mapToLong(ContagemMunicipio::total).sum();
        return new PorMunicipioResponse(ReferenciaDto.FONTE, false, atualizadoEm(repository.importacoes()),
                ReferenciaDto.AVISO, total, itens);
    }

    /** Detalhe de um ponto para o painel do mapa; 404 se não existir. A área só aparece quando a fonte a informa. */
    public PontoDetalhe detalhe(long id) {
        Registro r = repository.registro(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Ponto de satélite", id));
        Map<String, Object> atributos = GeoJson.objeto(r.atributos());
        Ponto p = r.ponto();
        boolean deter = TipoReferencia.DESMATAMENTO.name().equals(p.tipo());
        boolean informada = deter && p.areaKm2() != null;
        String campo = informada ? campoArea(atributos) : null;
        BigDecimal km2Fonte = informada ? valorDaFonte(atributos, campo, p.areaKm2()) : null;
        if (km2Fonte != null) {
            // hectares a partir do valor completo da fonte (a coluna AREA_KM2 guarda só 4 casas)
            p = new Ponto(p.id(), p.tipo(), p.dataHora(), p.latitude(), p.longitude(), p.municipioId(),
                    p.municipio(), p.bioma(), p.satelite(), p.classe(), p.areaKm2(), GeoJson.km2ParaHectares(km2Fonte));
        }
        String observacao;
        if (!deter) {
            observacao = ReferenciaDto.AREA_FOCO_NAO_INFORMADA;
        } else if (informada) {
            observacao = "Área do polígono do alerta informada pelo INPE/TerraBrasilis (campo " + campo
                    + ", em km²), convertida para hectares (1 km² = 100 ha).";
        } else {
            observacao = "Área afetada: não informada pela fonte para este alerta.";
        }
        return new PontoDetalhe(ReferenciaDto.FONTE, false, ReferenciaDto.AVISO, p, informada, campo, km2Fonte,
                observacao,
                deter ? GeoJson.geometria(r.geometria()) : null, atributos, r.importacao());
    }

    private static BigDecimal valorDaFonte(Map<String, Object> atributos, String campo, BigDecimal coluna) {
        Object v = atributos == null ? null : atributos.get(campo);
        if (v instanceof Number || v instanceof String) {
            try {
                return new BigDecimal(v.toString());
            } catch (NumberFormatException e) {
                return coluna;
            }
        }
        return coluna;
    }

    /** areamunkm (área do recorte desenhado; Amazônia e Cerrado) ou, na falta dela, areatotalkm. */
    static String campoArea(Map<String, Object> atributos) {
        if (atributos != null && atributos.get("areamunkm") == null && atributos.get("areatotalkm") != null) {
            return "areatotalkm";
        }
        return "areamunkm";
    }

    private static void validar(ReferenciaFiltro f) {
        if (f.de() != null && f.ate() != null && f.de().isAfter(f.ate())) {
            throw new DadosInvalidosException("de", "A data inicial não pode ser posterior à data final.");
        }
    }

    private static LocalDateTime atualizadoEm(List<Importacao> imp) {
        return imp.stream().map(Importacao::importadoEm).max(Comparator.naturalOrder()).orElse(null);
    }
}
