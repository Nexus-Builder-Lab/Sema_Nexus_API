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
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DecisaoTriagem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DistribuicaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TriagemRequest;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TriagemDistribuicaoServiceTest {

    static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC);
    static final LocalDate HOJE = LocalDate.of(2026, 11, 11);

    @Mock DemandaRepository demandas;
    @Mock DemandaHistoricoRepository historicos;
    @Mock DistribuicaoRepository distribuicoes;
    @Mock EquipeRepository equipes;
    @Mock UsuarioRepository usuarios;
    @Mock AuditService auditoria;
    @Mock DemandaService demandaService;

    TriagemService triagem;
    DistribuicaoService distribuicao;
    Usuario gestor;
    UsuarioAutenticado ator;
    Equipe equipe;
    Usuario fiscal;

    @BeforeEach
    void preparar() {
        DemandaStateMachine maquina = new DemandaStateMachine();
        triagem = new TriagemService(demandas, historicos, usuarios, maquina, demandaService, auditoria);
        distribuicao = new DistribuicaoService(demandas, historicos, distribuicoes, equipes, usuarios, maquina,
                demandaService, auditoria, RELOGIO);
        gestor = usuario(10L, Perfil.GESTOR);
        ator = UsuarioAutenticado.de(gestor);
        when(usuarios.findById(10L)).thenReturn(Optional.of(gestor));
        equipe = new Equipe("Equipe Cerrado", mock(Setor.class));
        ReflectionTestUtils.setField(equipe, "id", 1L);
        fiscal = usuario(20L, Perfil.FISCAL);
        when(equipes.findById(1L)).thenReturn(Optional.of(equipe));
        when(equipes.findMembros(1L)).thenReturn(List.of(fiscal));
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
        d.setOrigem(Origem.DENUNCIA);
        d.setMunicipio(mock(Municipio.class));
        when(demandas.buscarParaEscrita(5L)).thenReturn(Optional.of(d));
        return d;
    }

    private static TriagemRequest classificar() {
        return new TriagemRequest(DecisaoTriagem.CLASSIFICAR, CategoriaFiscalizacao.values()[0], Prioridade.ALTA,
                "Indícios compatíveis.");
    }

    private DemandaHistorico historicoSalvo() {
        ArgumentCaptor<DemandaHistorico> h = ArgumentCaptor.forClass(DemandaHistorico.class);
        verify(historicos).save(h.capture());
        return h.getValue();
    }

    // ---------------------------------------------------------------- triagem

    @Test
    void iniciarTriagemMovePendenteParaEmTriagemComHistoricoEAuditoria() {
        Demanda d = demanda(StatusDemanda.PENDENTE);

        triagem.iniciar(ator, 5L);

        assertThat(d.getStatus()).isEqualTo(StatusDemanda.EM_TRIAGEM);
        DemandaHistorico h = historicoSalvo();
        assertThat(h.getTipo()).isEqualTo(TipoHistorico.TRIAGEM);
        assertThat(h.getStatusAnterior()).isEqualTo(StatusDemanda.PENDENTE);
        assertThat(h.getStatusNovo()).isEqualTo(StatusDemanda.EM_TRIAGEM);
        assertThat(h.getUsuario()).isSameAs(gestor);
        verify(auditoria).registrar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void iniciarTriagemForaDePendenteRespondeConflitoSemGravar() {
        demanda(StatusDemanda.EM_TRIAGEM);

        assertThatThrownBy(() -> triagem.iniciar(ator, 5L)).isInstanceOf(ConflitoException.class);
        verify(historicos, never()).save(any());
        verify(auditoria, never()).registrar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void demandaInexistenteRetorna404() {
        when(demandas.buscarParaEscrita(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> triagem.iniciar(ator, 9L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void classificarGravaCategoriaPrioridadeEJustificativa() {
        Demanda d = demanda(StatusDemanda.EM_TRIAGEM);

        triagem.triar(ator, 5L, classificar());

        assertThat(d.getStatus()).isEqualTo(StatusDemanda.AGUARDANDO_DISTRIBUICAO);
        assertThat(d.getCategoria()).isEqualTo(CategoriaFiscalizacao.values()[0]);
        assertThat(d.getPrioridade()).isEqualTo(Prioridade.ALTA);
        DemandaHistorico h = historicoSalvo();
        assertThat(h.getTipo()).isEqualTo(TipoHistorico.TRIAGEM);
        assertThat(h.getTexto()).contains("Indícios compatíveis.");
        assertThat(h.getStatusAnterior()).isEqualTo(StatusDemanda.EM_TRIAGEM);
    }

    @Test
    void classificarSemCategoriaOuPrioridadeRetorna400() {
        Demanda d = demanda(StatusDemanda.EM_TRIAGEM);
        var pedido = new TriagemRequest(DecisaoTriagem.CLASSIFICAR, null, null, "Justificativa");

        assertThatThrownBy(() -> triagem.triar(ator, 5L, pedido)).isInstanceOf(DadosInvalidosException.class);
        assertThat(d.getStatus()).isEqualTo(StatusDemanda.EM_TRIAGEM);
        verify(historicos, never()).save(any());
    }

    @Test
    void complementarVoltaParaPendenteERegistraComplementacao() {
        Demanda d = demanda(StatusDemanda.EM_TRIAGEM);

        triagem.triar(ator, 5L, new TriagemRequest(DecisaoTriagem.SOLICITAR_COMPLEMENTACAO, null, null,
                "Falta a localização."));

        assertThat(d.getStatus()).isEqualTo(StatusDemanda.PENDENTE);
        DemandaHistorico h = historicoSalvo();
        assertThat(h.getTipo()).isEqualTo(TipoHistorico.COMPLEMENTACAO);
        assertThat(h.getTexto()).contains("Falta a localização.");
    }

    @Test
    void triarForaDeEmTriagemRespondeConflito() {
        demanda(StatusDemanda.PENDENTE);

        assertThatThrownBy(() -> triagem.triar(ator, 5L, classificar())).isInstanceOf(ConflitoException.class);
    }

    // ---------------------------------------------------------------- distribuição

    @Test
    void distribuirGravaEquipeFiscalPrazoDistribuicaoEHistorico() {
        Demanda d = demanda(StatusDemanda.AGUARDANDO_DISTRIBUICAO);

        distribuicao.distribuir(ator, 5L, new DistribuicaoRequest(1L, 20L, HOJE, " Urgente "));

        assertThat(d.getStatus()).isEqualTo(StatusDemanda.DISTRIBUIDA);
        assertThat(d.getEquipe()).isSameAs(equipe);
        assertThat(d.getFiscal()).isSameAs(fiscal);
        assertThat(d.getPrazo()).isEqualTo(HOJE);
        ArgumentCaptor<Distribuicao> dist = ArgumentCaptor.forClass(Distribuicao.class);
        verify(distribuicoes).save(dist.capture());
        assertThat(dist.getValue().getObservacoes()).isEqualTo("Urgente");
        assertThat(dist.getValue().getUsuario()).isSameAs(gestor);
        DemandaHistorico h = historicoSalvo();
        assertThat(h.getTipo()).isEqualTo(TipoHistorico.DISTRIBUICAO);
        assertThat(h.getStatusAnterior()).isEqualTo(StatusDemanda.AGUARDANDO_DISTRIBUICAO);
        assertThat(h.getStatusNovo()).isEqualTo(StatusDemanda.DISTRIBUIDA);
        verify(auditoria).registrar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void fiscalForaDaEquipeRetorna400SemGravar() {
        Demanda d = demanda(StatusDemanda.AGUARDANDO_DISTRIBUICAO);

        assertThatThrownBy(() -> distribuicao.distribuir(ator, 5L, new DistribuicaoRequest(1L, 99L, HOJE, null)))
                .isInstanceOf(DadosInvalidosException.class);
        assertThat(d.getStatus()).isEqualTo(StatusDemanda.AGUARDANDO_DISTRIBUICAO);
        verify(distribuicoes, never()).save(any());
    }

    @Test
    void prazoNoPassadoRetorna400() {
        demanda(StatusDemanda.AGUARDANDO_DISTRIBUICAO);

        assertThatThrownBy(() -> distribuicao.distribuir(ator, 5L,
                new DistribuicaoRequest(1L, 20L, HOJE.minusDays(1), null)))
                .isInstanceOf(DadosInvalidosException.class);
    }

    @Test
    void equipeInexistenteOuInativaRetorna400() {
        demanda(StatusDemanda.AGUARDANDO_DISTRIBUICAO);
        assertThatThrownBy(() -> distribuicao.distribuir(ator, 5L, new DistribuicaoRequest(7L, 20L, HOJE, null)))
                .isInstanceOf(DadosInvalidosException.class);

        equipe.setAtivo(false);
        assertThatThrownBy(() -> distribuicao.distribuir(ator, 5L, new DistribuicaoRequest(1L, 20L, HOJE, null)))
                .isInstanceOf(DadosInvalidosException.class);
    }

    @Test
    void distribuirForaDeAguardandoDistribuicaoRespondeConflito() {
        demanda(StatusDemanda.PENDENTE);

        assertThatThrownBy(() -> distribuicao.distribuir(ator, 5L, new DistribuicaoRequest(1L, 20L, HOJE, null)))
                .isInstanceOf(ConflitoException.class);
        verify(distribuicoes, never()).save(any());
    }
}
