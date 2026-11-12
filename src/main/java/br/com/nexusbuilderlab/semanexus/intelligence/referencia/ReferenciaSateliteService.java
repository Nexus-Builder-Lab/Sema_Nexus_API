package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.ContagemMunicipio;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.Importacao;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.Metadados;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.Ponto;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.PontosResponse;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.PorMunicipioResponse;
import java.time.LocalDateTime;
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

    private static void validar(ReferenciaFiltro f) {
        if (f.de() != null && f.ate() != null && f.de().isAfter(f.ate())) {
            throw new DadosInvalidosException("de", "A data inicial não pode ser posterior à data final.");
        }
    }

    private static LocalDateTime atualizadoEm(List<Importacao> imp) {
        return imp.stream().map(Importacao::importadoEm).max(Comparator.naturalOrder()).orElse(null);
    }
}
