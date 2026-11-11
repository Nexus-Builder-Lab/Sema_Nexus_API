package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.GlobalExceptionHandler.ErroCampo;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaStateMachine.Acao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DecisaoTriagem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TriagemRequest;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Triagem de demandas: iniciar, classificar (→ Aguardando distribuição) ou solicitar complementação (→ Pendente). */
@Service
public class TriagemService {

    private final DemandaRepository demandas;
    private final DemandaHistoricoRepository historicos;
    private final UsuarioRepository usuarios;
    private final DemandaStateMachine maquina;
    private final DemandaService demandaService;
    private final AuditService auditoria;

    public TriagemService(DemandaRepository demandas, DemandaHistoricoRepository historicos,
                          UsuarioRepository usuarios, DemandaStateMachine maquina, DemandaService demandaService,
                          AuditService auditoria) {
        this.demandas = demandas;
        this.historicos = historicos;
        this.usuarios = usuarios;
        this.maquina = maquina;
        this.demandaService = demandaService;
        this.auditoria = auditoria;
    }

    /** PENDENTE → EM_TRIAGEM (409 em qualquer outro status). */
    @Transactional
    public DemandaDetalhe iniciar(UsuarioAutenticado ator, Long id) {
        Demanda demanda = obterParaEscrita(id);
        StatusDemanda anterior = demanda.getStatus();
        StatusDemanda novo = maquina.transicionar(anterior, Acao.INICIAR_TRIAGEM);
        Usuario autor = usuario(ator);
        demanda.setStatus(novo);
        demandas.save(demanda);
        historicos.save(new DemandaHistorico(demanda, TipoHistorico.TRIAGEM, anterior, novo,
                "Triagem iniciada.", autor));
        auditoria.registrar("TRIAGEM_INICIAR", DemandaService.ENTIDADE, String.valueOf(id),
                "Triagem da demanda " + demanda.getNumero() + " iniciada.");
        return demandaService.detalhar(ator, id);
    }

    /** EM_TRIAGEM → AGUARDANDO_DISTRIBUICAO (classificar) ou → PENDENTE (solicitar complementação). */
    @Transactional
    public DemandaDetalhe triar(UsuarioAutenticado ator, Long id, TriagemRequest pedido) {
        Demanda demanda = obterParaEscrita(id);
        boolean classificar = pedido.decisao() == DecisaoTriagem.CLASSIFICAR;
        StatusDemanda anterior = demanda.getStatus();
        StatusDemanda novo = maquina.transicionar(anterior,
                classificar ? Acao.CLASSIFICAR : Acao.SOLICITAR_COMPLEMENTACAO);
        if (classificar) {
            validarClassificacao(pedido);
            demanda.setCategoria(pedido.categoria());
            demanda.setPrioridade(pedido.prioridade());
        }
        Usuario autor = usuario(ator);
        String justificativa = pedido.justificativa().trim();
        demanda.setStatus(novo);
        demandas.save(demanda);
        String texto = classificar
                ? "Triagem concluída: categoria " + pedido.categoria().rotulo() + ", prioridade "
                        + pedido.prioridade().rotulo() + ". Justificativa: " + justificativa
                : "Complementação solicitada: " + justificativa;
        historicos.save(new DemandaHistorico(demanda,
                classificar ? TipoHistorico.TRIAGEM : TipoHistorico.COMPLEMENTACAO, anterior, novo, texto, autor));
        auditoria.registrar(classificar ? "TRIAGEM_CONCLUIR" : "TRIAGEM_COMPLEMENTAR", DemandaService.ENTIDADE,
                String.valueOf(id), "Demanda " + demanda.getNumero() + ": " + texto);
        return demandaService.detalhar(ator, id);
    }

    private static void validarClassificacao(TriagemRequest pedido) {
        List<ErroCampo> erros = new ArrayList<>();
        if (pedido.categoria() == null) {
            erros.add(new ErroCampo("categoria", "A categoria é obrigatória para concluir a triagem."));
        }
        if (pedido.prioridade() == null) {
            erros.add(new ErroCampo("prioridade", "A prioridade é obrigatória para concluir a triagem."));
        }
        if (!erros.isEmpty()) {
            throw new DadosInvalidosException(erros);
        }
    }

    private Demanda obterParaEscrita(Long id) {
        return demandas.buscarParaEscrita(id).orElseThrow(() -> RecursoNaoEncontradoException.de("Demanda", id));
    }

    private Usuario usuario(UsuarioAutenticado ator) {
        return usuarios.findById(ator.id()).orElseThrow(() -> RecursoNaoEncontradoException.de("Usuário", ator.id()));
    }
}
