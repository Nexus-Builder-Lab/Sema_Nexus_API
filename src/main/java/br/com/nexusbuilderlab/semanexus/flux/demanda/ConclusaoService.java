package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaStateMachine.Acao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ConclusaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DevolucaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Encerramento pelo gestor: conclusão (→ FINALIZADA) ou devolução ao fiscal (→ EM_ANDAMENTO). */
@Service
public class ConclusaoService {

    private final DemandaRepository demandas;
    private final DemandaHistoricoRepository historicos;
    private final FiscalizacaoRepository fiscalizacoes;
    private final UsuarioRepository usuarios;
    private final DemandaStateMachine maquina;
    private final DemandaService demandaService;
    private final AuditService auditoria;
    private final Clock clock;

    public ConclusaoService(DemandaRepository demandas, DemandaHistoricoRepository historicos,
                            FiscalizacaoRepository fiscalizacoes, UsuarioRepository usuarios,
                            DemandaStateMachine maquina, DemandaService demandaService, AuditService auditoria,
                            Clock clock) {
        this.demandas = demandas;
        this.historicos = historicos;
        this.fiscalizacoes = fiscalizacoes;
        this.usuarios = usuarios;
        this.maquina = maquina;
        this.demandaService = demandaService;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** 409 se fora de AGUARDANDO_CONCLUSAO (inclui FINALIZADA) ou sem registro de fiscalização. */
    @Transactional
    public DemandaDetalhe concluir(UsuarioAutenticado ator, Long id, ConclusaoRequest pedido) {
        Demanda demanda = obter(id);
        StatusDemanda anterior = demanda.getStatus();
        StatusDemanda novo = maquina.transicionar(anterior, Acao.CONCLUIR);
        if (!fiscalizacoes.existsByDemandaId(id)) {
            throw new ConflitoException("Não é possível concluir: a demanda não tem registro de fiscalização.");
        }
        Usuario autor = autor(ator);
        demanda.setStatus(novo);
        demanda.setResultadoFinal(pedido.resultadoFinal().trim());
        demanda.setJustificativaConclusao(pedido.justificativa().trim());
        demanda.setConcluidaEm(LocalDateTime.now(clock.withZone(DemandaService.FUSO)));
        demandas.save(demanda);
        historicos.save(ExecucaoSuporte.evento(demanda, TipoHistorico.CONCLUSAO, anterior, novo,
                "Demanda finalizada. Justificativa: " + pedido.justificativa().trim(), autor));
        auditoria.registrar("CONCLUIR", DemandaService.ENTIDADE, String.valueOf(id),
                "Demanda " + demanda.getNumero() + " finalizada.");
        return demandaService.detalhar(ator, id);
    }

    /** 409 se fora de AGUARDANDO_CONCLUSAO. */
    @Transactional
    public DemandaDetalhe devolver(UsuarioAutenticado ator, Long id, DevolucaoRequest pedido) {
        Demanda demanda = obter(id);
        StatusDemanda anterior = demanda.getStatus();
        StatusDemanda novo = maquina.transicionar(anterior, Acao.DEVOLVER);
        Usuario autor = autor(ator);
        demanda.setStatus(novo);
        demandas.save(demanda);
        historicos.save(ExecucaoSuporte.evento(demanda, TipoHistorico.DEVOLUCAO, anterior, novo,
                "Devolvida ao fiscal para ajustes: " + pedido.justificativa().trim(), autor));
        auditoria.registrar("DEVOLVER", DemandaService.ENTIDADE, String.valueOf(id),
                "Demanda " + demanda.getNumero() + " devolvida para ajustes.");
        return demandaService.detalhar(ator, id);
    }

    private Demanda obter(Long id) {
        return demandas.buscarParaEscrita(id).orElseThrow(() -> RecursoNaoEncontradoException.de("Demanda", id));
    }

    private Usuario autor(UsuarioAutenticado ator) {
        return usuarios.findById(ator.id()).orElseThrow(() -> RecursoNaoEncontradoException.de("Usuário", ator.id()));
    }
}
