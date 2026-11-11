package br.com.nexusbuilderlab.semanexus.core.dashboard;

import br.com.nexusbuilderlab.semanexus.common.dto.AvisoDemonstrativo;
import br.com.nexusbuilderlab.semanexus.common.dto.DemandaRef;
import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.core.dashboard.dto.AtividadeRecente;
import br.com.nexusbuilderlab.semanexus.core.dashboard.dto.DashboardResponse;
import br.com.nexusbuilderlab.semanexus.flux.demanda.Demanda;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaHistoricoRepository;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.IndicadorService;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorFiltro;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta o dashboard inicial a partir dos indicadores e dos últimos eventos das demandas. Dados simulados. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int ATIVIDADES = 10;

    private final IndicadorService indicadores;
    private final DemandaHistoricoRepository historico;

    public DashboardService(IndicadorService indicadores, DemandaHistoricoRepository historico) {
        this.indicadores = indicadores;
        this.historico = historico;
    }

    public DashboardResponse dashboard() {
        IndicadorFiltro semFiltro = new IndicadorFiltro(null, null, null, null, null, null);
        List<Demanda> demandas = indicadores.demandasDoFiltro(semFiltro);
        List<AtividadeRecente> atividades = historico.findRecentes(PageRequest.of(0, ATIVIDADES)).stream()
                .map(h -> new AtividadeRecente(h.getData(), h.getTipo(), h.getTexto(),
                        new DemandaRef(h.getDemanda().getId(), h.getDemanda().getNumero(), h.getDemanda().getTitulo()),
                        UsuarioRef.de(h.getUsuario())))
                .toList();
        return new DashboardResponse(true, AvisoDemonstrativo.TEXTO,
                indicadores.resumir(demandas, indicadores.fiscalizacoes(semFiltro), indicadores.totalDocumentos()),
                IndicadorService.contagemSituacao(demandas), atividades,
                IndicadorService.contagemBioma(demandas), IndicadorService.contagemCategoria(demandas));
    }
}
