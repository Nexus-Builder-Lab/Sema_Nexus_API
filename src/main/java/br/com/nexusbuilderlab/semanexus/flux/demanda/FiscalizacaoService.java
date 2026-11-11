package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaStateMachine.Acao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FiscalizacaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro (simulado) da fiscalização (EM_ANDAMENTO → AGUARDANDO_CONCLUSAO). Não gera auto de infração nem substitui
 * sistemas oficiais. Registro 1:1: após devolução o reenvio atualiza o mesmo registro.
 */
@Service
public class FiscalizacaoService {

    private final DemandaRepository demandas;
    private final DemandaHistoricoRepository historicos;
    private final FiscalizacaoRepository fiscalizacoes;
    private final UsuarioRepository usuarios;
    private final DemandaStateMachine maquina;
    private final DemandaService demandaService;
    private final AuditService auditoria;
    private final Clock clock;

    public FiscalizacaoService(DemandaRepository demandas, DemandaHistoricoRepository historicos,
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

    @Transactional
    public DemandaDetalhe registrar(UsuarioAutenticado ator, Long id, FiscalizacaoRequest pedido) {
        Demanda demanda = demandas.buscarParaEscrita(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Demanda", id));
        ExecucaoSuporte.exigirFiscalDesignado(ator, demanda);
        StatusDemanda anterior = demanda.getStatus();
        StatusDemanda novo = maquina.transicionar(anterior, Acao.REGISTRAR_FISCALIZACAO);
        if (pedido.data().isAfter(LocalDate.now(clock.withZone(DemandaService.FUSO)))) {
            throw new DadosInvalidosException("data", "A data da fiscalização não pode ser futura.");
        }
        if (demanda.getEquipe() == null) {
            throw new ConflitoException("A demanda não tem equipe designada.");
        }
        Usuario autor = usuarios.findById(ator.id())
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuário", ator.id()));
        String atividades = pedido.atividades().trim();
        String resultado = pedido.resultado().trim();
        String evidencias = pedido.evidencias().trim();
        String recomendacoes = pedido.recomendacoes() == null || pedido.recomendacoes().isBlank()
                ? null : pedido.recomendacoes().trim();

        Fiscalizacao f = fiscalizacoes.buscarPorDemanda(id).orElse(null);
        if (f == null) {
            fiscalizacoes.save(new Fiscalizacao(demanda, pedido.data(), demanda.getEquipe(), atividades, resultado,
                    evidencias, recomendacoes, autor));
        } else {
            f.atualizar(pedido.data(), atividades, resultado, evidencias, recomendacoes);
            fiscalizacoes.save(f);
        }
        demanda.setStatus(novo);
        demandas.save(demanda);
        historicos.save(ExecucaoSuporte.evento(demanda, TipoHistorico.FISCALIZACAO, anterior, novo,
                "Fiscalização de " + pedido.data() + " registrada. Resultado: " + resultado, autor));
        auditoria.registrar("REGISTRAR_FISCALIZACAO", DemandaService.ENTIDADE, String.valueOf(id),
                "Demanda " + demanda.getNumero() + ": fiscalização registrada, aguardando conclusão.");
        return demandaService.detalhar(ator, id);
    }
}
