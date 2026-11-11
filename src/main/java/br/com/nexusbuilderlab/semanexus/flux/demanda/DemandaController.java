package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.common.exception.ContratoPendenteException;
import br.com.nexusbuilderlab.semanexus.common.openapi.ContratoPendente;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.AtividadeRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ConclusaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaFiltro;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaResumo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DevolucaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DistribuicaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FiscalizacaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.HistoricoItem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TriagemRequest;
import br.com.nexusbuilderlab.semanexus.security.Autorizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Flux (demandas de fiscalização). Cadastro, edição, listagem, detalhes, histórico e exclusão estão implementados
 * (#11); triagem e distribuição estão implementadas (#12); os demais respondem `501` até a Issue indicada em cada
 * {@link ContratoPendente}: acompanhamento, fiscalização, conclusão e devolução (#13). Máquina de estados em
 * `docs/ARCHITECTURE.md` §5.
 */
@RestController
@RequestMapping("/api/demandas")
@Tag(name = "Flux — Demandas", description = "Demandas de fiscalização ambiental: cadastro, triagem, distribuição, "
        + "acompanhamento, fiscalização, conclusão e devolução")
public class DemandaController {

    private final DemandaService service;
    private final TriagemService triagem;
    private final DistribuicaoService distribuicao;

    public DemandaController(DemandaService service, TriagemService triagem, DistribuicaoService distribuicao) {
        this.service = service;
        this.triagem = triagem;
        this.distribuicao = distribuicao;
    }

    // ----------------------------------------------------------------- Issue #11 — cadastro e consulta

    @Operation(summary = "Cadastra uma demanda (status inicial PENDENTE, número DEM-AAAA/NNNN automático)",
            description = "Perfis: ADMIN, GESTOR, ANALISTA.")
    @ApiResponse(responseCode = "201", description = "Demanda criada; `Location` aponta para `/api/demandas/{id}`")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR_ANALISTA)
    @PostMapping
    public ResponseEntity<DemandaDetalhe> cadastrar(@AuthenticationPrincipal UsuarioAutenticado ator,
                                                    @Valid @RequestBody DemandaRequest pedido) {
        DemandaDetalhe criada = service.cadastrar(ator, pedido);
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(local).body(criada);
    }

    @Operation(summary = "Edita uma demanda (somente em PENDENTE ou EM_TRIAGEM)",
            description = "Perfis: ADMIN, GESTOR, ANALISTA. Fora desses status responde 409.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR_ANALISTA)
    @PutMapping("/{id}")
    public DemandaDetalhe editar(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id,
                                 @Valid @RequestBody DemandaRequest pedido) {
        return service.editar(ator, id, pedido);
    }

    @Operation(summary = "Lista demandas com paginação, ordenação e filtros combináveis",
            description = "Qualquer perfil autenticado; FISCAL enxerga só as demandas da sua equipe ou atribuídas a ele. "
                    + "Ordenação: `sort=campo,asc|desc` (campos: numero, titulo, dataRecebimento, prazo, status, prioridade); "
                    + "sem `sort`, mais recentes primeiro.")
    @GetMapping
    public PageResponse<DemandaResumo> listar(@AuthenticationPrincipal UsuarioAutenticado ator,
                                              @ParameterObject DemandaFiltro filtro,
                                              @ParameterObject Pageable paginacao) {
        return service.listar(ator, filtro, paginacao);
    }

    @Operation(summary = "Detalhes da demanda (equipe, fiscal, prazo, situação, fiscalização e documentos)",
            description = "Qualquer perfil autenticado; FISCAL só nas demandas da sua equipe ou atribuídas a ele.")
    @ApiResponse(responseCode = "403", description = "Fiscal fora do escopo (demanda de outra equipe)")
    @GetMapping("/{id}")
    public DemandaDetalhe detalhar(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id) {
        return service.detalhar(ator, id);
    }

    @Operation(summary = "Histórico (linha do tempo) da demanda, do mais recente ao mais antigo",
            description = "Qualquer perfil autenticado; FISCAL só nas demandas da sua equipe ou atribuídas a ele.")
    @ApiResponse(responseCode = "403", description = "Fiscal fora do escopo (demanda de outra equipe)")
    @GetMapping("/{id}/historico")
    public List<HistoricoItem> historico(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id) {
        return service.historico(ator, id);
    }

    @Operation(summary = "Exclui logicamente a demanda (somente ADMIN e somente em PENDENTE)",
            description = "Perfil: ADMIN. Fora de PENDENTE responde 409.")
    @PreAuthorize(Autorizacao.SOMENTE_ADMIN)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id) {
        service.excluir(ator, id);
    }

    // ----------------------------------------------------------------- Issue #12 — triagem e distribuição

    @Operation(summary = "Inicia a triagem (PENDENTE → EM_TRIAGEM)", description = "Perfis: ADMIN, GESTOR, ANALISTA. Fora de PENDENTE responde 409.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR_ANALISTA)
    @PostMapping("/{id}/triagem/iniciar")
    public DemandaDetalhe iniciarTriagem(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id) {
        return triagem.iniciar(ator, id);
    }

    @Operation(summary = "Conclui a triagem (EM_TRIAGEM → AGUARDANDO_DISTRIBUICAO) ou solicita complementação (→ PENDENTE)",
            description = "Perfis: ADMIN, GESTOR, ANALISTA. CLASSIFICAR exige categoria, prioridade e justificativa (400 por campo). "
                    + "Fora de EM_TRIAGEM responde 409.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR_ANALISTA)
    @PostMapping("/{id}/triagem")
    public DemandaDetalhe triar(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id,
                              @Valid @RequestBody TriagemRequest pedido) {
        return triagem.triar(ator, id, pedido);
    }

    @Operation(summary = "Distribui a demanda (AGUARDANDO_DISTRIBUICAO → DISTRIBUIDA)",
            description = "Perfis: ADMIN, GESTOR. Exige equipe, fiscal membro da equipe e prazo >= hoje "
                    + "(400 se o fiscal não é membro da equipe). Fora de AGUARDANDO_DISTRIBUICAO responde 409.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR)
    @PostMapping("/{id}/distribuicao")
    public DemandaDetalhe distribuir(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable Long id,
                                  @Valid @RequestBody DistribuicaoRequest pedido) {
        return distribuicao.distribuir(ator, id, pedido);
    }

    // ----------------------------------------------------------------- Issue #13 — acompanhamento e conclusão

    @Operation(summary = "Inicia a execução (DISTRIBUIDA → EM_ANDAMENTO)",
            description = "Perfis: fiscal designado, GESTOR, ADMIN. Fiscal que não é o designado recebe 403.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR_FISCAL)
    @PostMapping("/{id}/inicio")
    @ContratoPendente(issue = 13)
    public DemandaDetalhe iniciarExecucao(@PathVariable Long id) {
        throw new ContratoPendenteException(13);
    }

    @Operation(summary = "Registra uma atividade de acompanhamento no histórico (demanda EM_ANDAMENTO)",
            description = "Perfis: fiscal designado, GESTOR, ADMIN.")
    @ApiResponse(responseCode = "201", description = "Atividade registrada")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR_FISCAL)
    @PostMapping("/{id}/atividades")
    @ResponseStatus(HttpStatus.CREATED)
    @ContratoPendente(issue = 13)
    public HistoricoItem registrarAtividade(@PathVariable Long id, @Valid @RequestBody AtividadeRequest pedido) {
        throw new ContratoPendenteException(13);
    }

    @Operation(summary = "Registra a fiscalização (EM_ANDAMENTO → AGUARDANDO_CONCLUSAO)",
            description = "Perfis: fiscal designado, GESTOR, ADMIN. Exige registro completo.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR_FISCAL)
    @PostMapping("/{id}/fiscalizacao")
    @ContratoPendente(issue = 13)
    public DemandaDetalhe registrarFiscalizacao(@PathVariable Long id, @Valid @RequestBody FiscalizacaoRequest pedido) {
        throw new ContratoPendenteException(13);
    }

    @Operation(summary = "Conclui a demanda (AGUARDANDO_CONCLUSAO → FINALIZADA)",
            description = "Perfis: GESTOR, ADMIN. Exige resultado final e justificativa.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR)
    @PostMapping("/{id}/conclusao")
    @ContratoPendente(issue = 13)
    public DemandaDetalhe concluir(@PathVariable Long id, @Valid @RequestBody ConclusaoRequest pedido) {
        throw new ContratoPendenteException(13);
    }

    @Operation(summary = "Devolve a demanda para ajustes (AGUARDANDO_CONCLUSAO → EM_ANDAMENTO)",
            description = "Perfis: GESTOR, ADMIN. Exige justificativa.")
    @PreAuthorize(Autorizacao.ADMIN_GESTOR)
    @PostMapping("/{id}/devolucao")
    @ContratoPendente(issue = 13)
    public DemandaDetalhe devolver(@PathVariable Long id, @Valid @RequestBody DevolucaoRequest pedido) {
        throw new ContratoPendenteException(13);
    }
}
