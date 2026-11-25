package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.AtividadeRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.ConclusaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DevolucaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FiscalizacaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.HistoricoItem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecucaoServicosTest {

    static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC);
    static final LocalDate HOJE = LocalDate.of(2026, 11, 11);

    @Mock DemandaRepository demandas;
    @Mock DemandaHistoricoRepository historicos;
    @Mock FiscalizacaoRepository fiscalizacoes;
    @Mock UsuarioRepository usuarios;
    @Mock AuditService auditoria;
    @Mock DemandaService demandaService;

    AcompanhamentoService acompanhamento;
    FiscalizacaoService fiscalizacaoService;
    ConclusaoService conclusao;
    Usuario gestor;
    Usuario fiscal;
    Equipe equipe;

    @BeforeEach
    void preparar() {
        DemandaStateMachine maquina = new DemandaStateMachine();
        acompanhamento = new AcompanhamentoService(demandas, historicos, usuarios, maquina, demandaService,
                auditoria, RELOGIO);
        fiscalizacaoService = new FiscalizacaoService(demandas, historicos, fiscalizacoes, usuarios, maquina,
                demandaService, auditoria, RELOGIO);
        conclusao = new ConclusaoService(demandas, historicos, fiscalizacoes, usuarios, maquina, demandaService,
                auditoria, RELOGIO);
        gestor = usuario(10L, Perfil.GESTOR);
        fiscal = usuario(20L, Perfil.FISCAL);
        when(usuarios.findById(10L)).thenReturn(Optional.of(gestor));
        when(usuarios.findById(20L)).thenReturn(Optional.of(fiscal));
        when(usuarios.findById(21L)).thenReturn(Optional.of(usuario(21L, Perfil.FISCAL)));
        equipe = new Equipe("Equipe Cerrado", mock(Setor.class));
        ReflectionTestUtils.setField(equipe, "id", 1L);
    }

    private static Usuario usuario(long id, Perfil perfil) {
        Usuario u = new Usuario("Usuário " + id, "u" + id + "@exemplo.test", "hash", perfil);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Demanda demanda(StatusDemanda status) {
        Demanda d = new Demanda("DEM-2026/0001", gestor);
        ReflectionTestUtils.setField(d, "id", 5L);
        d.setStatus(status);
        d.setMunicipio(mock(Municipio.class));
        d.setEquipe(equipe);
        d.setFiscal(fiscal);
        when(demandas.buscarParaEscrita(5L)).thenReturn(Optional.of(d));
        return d;
    }

    private static UsuarioAutenticado como(Usuario u) {
        return UsuarioAutenticado.de(u);
    }

    private DemandaHistorico historicoSalvo() {
        ArgumentCaptor<DemandaHistorico> h = ArgumentCaptor.forClass(DemandaHistorico.class);
        verify(historicos).save(h.capture());
        return h.getValue();
    }

    private static FiscalizacaoRequest fiscalizacao(LocalDate data) {
        return new FiscalizacaoRequest(data, "Vistoria", "Sem irregularidades", "Fotos simuladas", null);
    }

    // ------------------------------------------------------------------ início

    @Test
    void fiscalDesignadoIniciaExecucao() {
        Demanda d = demanda(StatusDemanda.DISTRIBUIDA);

        acompanhamento.iniciar(como(fiscal), 5L);

        assertThat(d.getStatus()).isEqualTo(StatusDemanda.EM_ANDAMENTO);
        DemandaHistorico h = historicoSalvo();
        assertThat(h.getTipo()).isEqualTo(TipoHistorico.INICIO);
        assertThat(h.getStatusNovo()).isEqualTo(StatusDemanda.EM_ANDAMENTO);
        verify(auditoria).registrar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void gestorIniciaQualquerDemandaDistribuida() {
        Demanda d = demanda(StatusDemanda.DISTRIBUIDA);

        acompanhamento.iniciar(como(gestor), 5L);

        assertThat(d.getStatus()).isEqualTo(StatusDemanda.EM_ANDAMENTO);
    }

    @Test
    void fiscalNaoDesignadoRecebe403EmTodasAsAcoesDeExecucao() {
        demanda(StatusDemanda.DISTRIBUIDA);
        UsuarioAutenticado outro = como(usuario(21L, Perfil.FISCAL));

        assertThatThrownBy(() -> acompanhamento.iniciar(outro, 5L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> acompanhamento.registrarAtividade(outro, 5L, new AtividadeRequest("x", null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> fiscalizacaoService.registrar(outro, 5L, fiscalizacao(HOJE)))
                .isInstanceOf(AccessDeniedException.class);
        verify(historicos, never()).save(any());
    }

    @Test
    void iniciarForaDeDistribuidaRespondeConflito() {
        demanda(StatusDemanda.EM_ANDAMENTO);

        assertThatThrownBy(() -> acompanhamento.iniciar(como(fiscal), 5L)).isInstanceOf(ConflitoException.class);
        verify(historicos, never()).save(any());
    }

    // ------------------------------------------------------------------ atividades

    @Test
    void atividadeGravaHistoricoSemMudarStatus() {
        Demanda d = demanda(StatusDemanda.EM_ANDAMENTO);

        HistoricoItem item = acompanhamento.registrarAtividade(como(fiscal), 5L,
                new AtividadeRequest("  Contato com o proprietário  ", null));

        assertThat(d.getStatus()).isEqualTo(StatusDemanda.EM_ANDAMENTO);
        DemandaHistorico h = historicoSalvo();
        assertThat(h.getTipo()).isEqualTo(TipoHistorico.ATIVIDADE);
        assertThat(h.getTexto()).isEqualTo("[2026-11-11] Contato com o proprietário");
        assertThat(item.tipo()).isEqualTo(TipoHistorico.ATIVIDADE);
    }

    @Test
    void atividadeForaDeEmAndamentoRespondeConflito() {
        demanda(StatusDemanda.DISTRIBUIDA);

        assertThatThrownBy(() -> acompanhamento.registrarAtividade(como(fiscal), 5L, new AtividadeRequest("x", null)))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void atividadeComDataFuturaRetorna400() {
        demanda(StatusDemanda.EM_ANDAMENTO);

        assertThatThrownBy(() -> acompanhamento.registrarAtividade(como(fiscal), 5L,
                new AtividadeRequest("x", HOJE.plusDays(1)))).isInstanceOf(DadosInvalidosException.class);
    }

    // ------------------------------------------------------------------ fiscalização

    @Test
    void registrarFiscalizacaoCriaRegistroEMoveParaAguardandoConclusao() {
        Demanda d = demanda(StatusDemanda.EM_ANDAMENTO);
        when(fiscalizacoes.buscarPorDemanda(5L)).thenReturn(Optional.empty());

        fiscalizacaoService.registrar(como(fiscal), 5L, fiscalizacao(HOJE));

        assertThat(d.getStatus()).isEqualTo(StatusDemanda.AGUARDANDO_CONCLUSAO);
        ArgumentCaptor<Fiscalizacao> f = ArgumentCaptor.forClass(Fiscalizacao.class);
        verify(fiscalizacoes).save(f.capture());
        assertThat(f.getValue().getEquipe()).isSameAs(equipe);
        assertThat(f.getValue().getRegistradoPor()).isSameAs(fiscal);
        assertThat(historicoSalvo().getTipo()).isEqualTo(TipoHistorico.FISCALIZACAO);
    }

    @Test
    void reenvioAposDevolucaoAtualizaORegistroExistente() {
        demanda(StatusDemanda.EM_ANDAMENTO);
        Fiscalizacao existente = new Fiscalizacao(mock(Demanda.class), HOJE.minusDays(3), equipe, "a", "r", "e", null,
                fiscal);
        when(fiscalizacoes.buscarPorDemanda(5L)).thenReturn(Optional.of(existente));

        fiscalizacaoService.registrar(como(fiscal), 5L, fiscalizacao(HOJE));

        assertThat(existente.getData()).isEqualTo(HOJE);
        assertThat(existente.getResultado()).isEqualTo("Sem irregularidades");
        verify(fiscalizacoes).save(existente);
    }

    @Test
    void reenvioSemChecklistPreservaOChecklistAnterior() {
        demanda(StatusDemanda.EM_ANDAMENTO);
        Fiscalizacao existente = new Fiscalizacao(mock(Demanda.class), HOJE.minusDays(3), equipe, "a", "r", "e", null,
                fiscal);
        existente.setChecklist("{\"versao\":1}");
        when(fiscalizacoes.buscarPorDemanda(5L)).thenReturn(Optional.of(existente));

        fiscalizacaoService.registrar(como(fiscal), 5L, fiscalizacao(HOJE));

        assertThat(existente.getChecklist()).isEqualTo("{\"versao\":1}");
    }

    @Test
    void fiscalizacaoComChecklistValidoGravaJsonDaCategoria() {
        Demanda d = demanda(StatusDemanda.EM_ANDAMENTO);
        d.setCategoria(br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao.QUEIMADA_INCENDIO);
        when(fiscalizacoes.buscarPorDemanda(5L)).thenReturn(Optional.empty());

        fiscalizacaoService.registrar(como(fiscal), 5L, new FiscalizacaoRequest(HOJE, "Vistoria", "Sem irregularidades",
                "Fotos simuladas", null, java.util.Map.of("local_acessivel", true, "foco_ativo", false,
                "area_atingida_ha", 3)));

        ArgumentCaptor<Fiscalizacao> f = ArgumentCaptor.forClass(Fiscalizacao.class);
        verify(fiscalizacoes).save(f.capture());
        assertThat(f.getValue().getChecklist()).contains("\"categoria\":\"QUEIMADA_INCENDIO\"").contains("\"versao\":1");
    }

    @Test
    void fiscalizacaoComChecklistInvalidoRetorna400SemGravar() {
        Demanda d = demanda(StatusDemanda.EM_ANDAMENTO);
        d.setCategoria(br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao.QUEIMADA_INCENDIO);

        assertThatThrownBy(() -> fiscalizacaoService.registrar(como(fiscal), 5L, new FiscalizacaoRequest(HOJE,
                "Vistoria", "Sem irregularidades", "Fotos simuladas", null,
                java.util.Map.of("local_acessivel", true, "item_desconhecido", 1))))
                .isInstanceOf(DadosInvalidosException.class);
        verify(fiscalizacoes, never()).save(any());
        assertThat(d.getStatus()).isEqualTo(StatusDemanda.EM_ANDAMENTO);
    }

    @Test
    void fiscalizacaoComDataFuturaRetorna400SemGravar() {
        demanda(StatusDemanda.EM_ANDAMENTO);

        assertThatThrownBy(() -> fiscalizacaoService.registrar(como(fiscal), 5L, fiscalizacao(HOJE.plusDays(1))))
                .isInstanceOf(DadosInvalidosException.class);
        verify(fiscalizacoes, never()).save(any());
    }

    @Test
    void fiscalizacaoForaDeEmAndamentoRespondeConflito() {
        demanda(StatusDemanda.DISTRIBUIDA);

        assertThatThrownBy(() -> fiscalizacaoService.registrar(como(fiscal), 5L, fiscalizacao(HOJE)))
                .isInstanceOf(ConflitoException.class);
        verify(fiscalizacoes, never()).save(any());
    }

    // ------------------------------------------------------------------ conclusão e devolução

    @Test
    void concluirFinalizaERegistraConcluidaEm() {
        Demanda d = demanda(StatusDemanda.AGUARDANDO_CONCLUSAO);
        when(fiscalizacoes.existsByDemandaId(5L)).thenReturn(true);

        conclusao.concluir(como(gestor), 5L, new ConclusaoRequest(" Regular ", " Sem pendências "));

        assertThat(d.getStatus()).isEqualTo(StatusDemanda.FINALIZADA);
        assertThat(d.getConcluidaEm()).isNotNull();
        assertThat(d.getResultadoFinal()).isEqualTo("Regular");
        assertThat(d.getJustificativaConclusao()).isEqualTo("Sem pendências");
        assertThat(historicoSalvo().getTipo()).isEqualTo(TipoHistorico.CONCLUSAO);
    }

    @Test
    void concluirSemRegistroDeFiscalizacaoRespondeConflito() {
        Demanda d = demanda(StatusDemanda.AGUARDANDO_CONCLUSAO);
        when(fiscalizacoes.existsByDemandaId(5L)).thenReturn(false);

        assertThatThrownBy(() -> conclusao.concluir(como(gestor), 5L, new ConclusaoRequest("r", "j")))
                .isInstanceOf(ConflitoException.class).hasMessageContaining("fiscalização");
        assertThat(d.getStatus()).isEqualTo(StatusDemanda.AGUARDANDO_CONCLUSAO);
        verify(historicos, never()).save(any());
    }

    @Test
    void demandaFinalizadaNaoAceitaNovasTransicoes() {
        demanda(StatusDemanda.FINALIZADA);
        when(fiscalizacoes.existsByDemandaId(5L)).thenReturn(true);

        assertThatThrownBy(() -> conclusao.concluir(como(gestor), 5L, new ConclusaoRequest("r", "j")))
                .isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> conclusao.devolver(como(gestor), 5L, new DevolucaoRequest("j")))
                .isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> acompanhamento.iniciar(como(gestor), 5L)).isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> acompanhamento.registrarAtividade(como(gestor), 5L, new AtividadeRequest("x", null)))
                .isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> fiscalizacaoService.registrar(como(gestor), 5L, fiscalizacao(HOJE)))
                .isInstanceOf(ConflitoException.class);
        verify(historicos, never()).save(any());
    }

    @Test
    void devolverVoltaParaEmAndamentoComHistorico() {
        Demanda d = demanda(StatusDemanda.AGUARDANDO_CONCLUSAO);

        conclusao.devolver(como(gestor), 5L, new DevolucaoRequest("Faltam fotos"));

        assertThat(d.getStatus()).isEqualTo(StatusDemanda.EM_ANDAMENTO);
        DemandaHistorico h = historicoSalvo();
        assertThat(h.getTipo()).isEqualTo(TipoHistorico.DEVOLUCAO);
        assertThat(h.getTexto()).contains("Faltam fotos");
    }

    @Test
    void devolverForaDeAguardandoConclusaoRespondeConflito() {
        demanda(StatusDemanda.EM_ANDAMENTO);

        assertThatThrownBy(() -> conclusao.devolver(como(gestor), 5L, new DevolucaoRequest("j")))
                .isInstanceOf(ConflitoException.class);
    }
}
