package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaStateMachine.Acao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.AtividadeRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.HistoricoItem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Acompanhamento da execução: início (DISTRIBUIDA → EM_ANDAMENTO) e registro de atividades. */
@Service
public class AcompanhamentoService {

    private final DemandaRepository demandas;
    private final DemandaHistoricoRepository historicos;
    private final UsuarioRepository usuarios;
    private final DemandaStateMachine maquina;
    private final DemandaService demandaService;
    private final AuditService auditoria;
    private final Clock clock;

    public AcompanhamentoService(DemandaRepository demandas, DemandaHistoricoRepository historicos,
                                 UsuarioRepository usuarios, DemandaStateMachine maquina,
                                 DemandaService demandaService, AuditService auditoria, Clock clock) {
        this.demandas = demandas;
        this.historicos = historicos;
        this.usuarios = usuarios;
        this.maquina = maquina;
        this.demandaService = demandaService;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** Inicia a execução: 403 se FISCAL não designado; 409 se fora de DISTRIBUIDA. */
    @Transactional
    public DemandaDetalhe iniciar(UsuarioAutenticado ator, Long id) {
        Demanda demanda = obter(id);
        ExecucaoSuporte.exigirFiscalDesignado(ator, demanda);
        StatusDemanda anterior = demanda.getStatus();
        StatusDemanda novo = maquina.transicionar(anterior, Acao.INICIAR_EXECUCAO);
        Usuario autor = autor(ator);
        demanda.setStatus(novo);
        demandas.save(demanda);
        historicos.save(ExecucaoSuporte.evento(demanda, TipoHistorico.INICIO, anterior, novo,
                "Execução da fiscalização iniciada.", autor));
        auditoria.registrar("INICIAR_EXECUCAO", DemandaService.ENTIDADE, String.valueOf(id),
                "Demanda " + demanda.getNumero() + " em andamento.");
        return demandaService.detalhar(ator, id);
    }

    /** Registra atividade no histórico (sem mudar o status): 403 se FISCAL não designado; 409 fora de EM_ANDAMENTO. */
    @Transactional
    public HistoricoItem registrarAtividade(UsuarioAutenticado ator, Long id, AtividadeRequest pedido) {
        Demanda demanda = obter(id);
        ExecucaoSuporte.exigirFiscalDesignado(ator, demanda);
        if (demanda.getStatus() != StatusDemanda.EM_ANDAMENTO) {
            throw new ConflitoException("Não é possível registrar atividade: a demanda está em \""
                    + demanda.getStatus().rotulo() + "\" e a ação exige \"" + StatusDemanda.EM_ANDAMENTO.rotulo()
                    + "\".");
        }
        LocalDate hoje = LocalDate.now(clock.withZone(DemandaService.FUSO));
        LocalDate data = pedido.data() == null ? hoje : pedido.data();
        if (data.isAfter(hoje)) {
            throw new DadosInvalidosException("data", "A data da atividade não pode ser futura.");
        }
        Usuario autor = autor(ator);
        DemandaHistorico h = ExecucaoSuporte.evento(demanda, TipoHistorico.ATIVIDADE, StatusDemanda.EM_ANDAMENTO,
                StatusDemanda.EM_ANDAMENTO, "[" + data + "] " + pedido.descricao().trim(), autor);
        historicos.save(h);
        auditoria.registrar("REGISTRAR_ATIVIDADE", DemandaService.ENTIDADE, String.valueOf(id),
                "Demanda " + demanda.getNumero() + ": atividade registrada.");
        return new HistoricoItem(h.getId(), h.getTipo(), h.getStatusAnterior(), h.getStatusNovo(), h.getTexto(),
                UsuarioRef.de(autor), h.getData());
    }

    private Demanda obter(Long id) {
        return demandas.buscarParaEscrita(id).orElseThrow(() -> RecursoNaoEncontradoException.de("Demanda", id));
    }

    private Usuario autor(UsuarioAutenticado ator) {
        return usuarios.findById(ator.id()).orElseThrow(() -> RecursoNaoEncontradoException.de("Usuário", ator.id()));
    }
}
