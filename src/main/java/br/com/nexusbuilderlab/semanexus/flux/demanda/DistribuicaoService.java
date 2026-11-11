package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaStateMachine.Acao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DistribuicaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Distribuição de demandas (AGUARDANDO_DISTRIBUICAO → DISTRIBUIDA): equipe, fiscal membro da equipe e prazo. */
@Service
public class DistribuicaoService {

    private final DemandaRepository demandas;
    private final DemandaHistoricoRepository historicos;
    private final DistribuicaoRepository distribuicoes;
    private final EquipeRepository equipes;
    private final UsuarioRepository usuarios;
    private final DemandaStateMachine maquina;
    private final DemandaService demandaService;
    private final AuditService auditoria;
    private final Clock clock;

    public DistribuicaoService(DemandaRepository demandas, DemandaHistoricoRepository historicos,
                               DistribuicaoRepository distribuicoes, EquipeRepository equipes,
                               UsuarioRepository usuarios, DemandaStateMachine maquina,
                               DemandaService demandaService, AuditService auditoria, Clock clock) {
        this.demandas = demandas;
        this.historicos = historicos;
        this.distribuicoes = distribuicoes;
        this.equipes = equipes;
        this.usuarios = usuarios;
        this.maquina = maquina;
        this.demandaService = demandaService;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** Distribui a demanda: 409 se fora de AGUARDANDO_DISTRIBUICAO; 400 para prazo, equipe ou fiscal inválidos. */
    @Transactional
    public DemandaDetalhe distribuir(UsuarioAutenticado ator, Long id, DistribuicaoRequest pedido) {
        Demanda demanda = demandas.buscarParaEscrita(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Demanda", id));
        StatusDemanda anterior = demanda.getStatus();
        StatusDemanda novo = maquina.transicionar(anterior, Acao.DISTRIBUIR);

        LocalDate hoje = LocalDate.now(clock.withZone(DemandaService.FUSO));
        if (pedido.prazo().isBefore(hoje)) {
            throw new DadosInvalidosException("prazo", "O prazo não pode ser anterior a hoje.");
        }
        Equipe equipe = equipes.findById(pedido.equipeId()).orElse(null);
        if (equipe == null || !equipe.isAtivo()) {
            throw new DadosInvalidosException("equipeId", "Equipe não encontrada ou inativa.");
        }
        Usuario fiscal = equipes.findMembros(equipe.getId()).stream()
                .filter(m -> m.getId().equals(pedido.fiscalId())).findFirst().orElse(null);
        if (fiscal == null) {
            throw new DadosInvalidosException("fiscalId", "O fiscal informado não é membro da equipe.");
        }
        if (!fiscal.isAtivo()) {
            throw new DadosInvalidosException("fiscalId", "O fiscal informado está inativo.");
        }
        Usuario autor = usuarios.findById(ator.id())
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuário", ator.id()));
        String observacoes = pedido.observacoes() == null || pedido.observacoes().isBlank()
                ? null : pedido.observacoes().trim();

        demanda.setStatus(novo);
        demanda.setEquipe(equipe);
        demanda.setFiscal(fiscal);
        demanda.setPrazo(pedido.prazo());
        demandas.save(demanda);
        distribuicoes.save(new Distribuicao(demanda, equipe, fiscal, pedido.prazo(), observacoes, autor));

        String texto = "Distribuída à equipe " + equipe.getNome() + ", fiscal " + fiscal.getNome() + ", prazo "
                + pedido.prazo() + (observacoes == null ? "." : ". Observações: " + observacoes);
        historicos.save(new DemandaHistorico(demanda, TipoHistorico.DISTRIBUICAO, anterior, novo, texto, autor));
        auditoria.registrar("DISTRIBUIR", DemandaService.ENTIDADE, String.valueOf(id),
                "Demanda " + demanda.getNumero() + ": " + texto);
        return demandaService.detalhar(ator, id);
    }
}
