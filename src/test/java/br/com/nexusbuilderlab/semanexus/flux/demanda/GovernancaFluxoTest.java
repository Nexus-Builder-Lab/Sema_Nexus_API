package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.com.nexusbuilderlab.semanexus.agentes.governanca.GovernancaIaService;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.notificacao.NotificacaoService;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DecisaoTriagem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DistribuicaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TriagemRequest;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** Issue #131: a decisão humana na triagem/distribuição é comparada com a última sugestão do agente. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GovernancaFluxoTest {

    static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC);

    @Mock DemandaRepository demandas;
    @Mock DemandaHistoricoRepository historicos;
    @Mock DistribuicaoRepository distribuicoes;
    @Mock EquipeRepository equipes;
    @Mock UsuarioRepository usuarios;
    @Mock AuditService auditoria;
    @Mock DemandaService demandaService;
    @Mock NotificacaoService notificacoes;
    @Mock GovernancaIaService governanca;

    TriagemService triagem;
    DistribuicaoService distribuicao;
    UsuarioAutenticado ator;
    Usuario gestor;

    @BeforeEach
    void preparar() {
        DemandaStateMachine maquina = new DemandaStateMachine();
        triagem = new TriagemService(demandas, historicos, usuarios, maquina, demandaService, auditoria);
        distribuicao = new DistribuicaoService(demandas, historicos, distribuicoes, equipes, usuarios, maquina,
                demandaService, auditoria, RELOGIO, notificacoes);
        triagem.setGovernanca(governanca);
        distribuicao.setGovernanca(governanca);
        gestor = usuario(10L, Perfil.GESTOR);
        ator = UsuarioAutenticado.de(gestor);
        when(usuarios.findById(10L)).thenReturn(Optional.of(gestor));
        Equipe equipe = new Equipe("Equipe Cerrado", mock(Setor.class));
        ReflectionTestUtils.setField(equipe, "id", 1L);
        when(equipes.findById(1L)).thenReturn(Optional.of(equipe));
        when(equipes.findMembros(1L)).thenReturn(List.of(usuario(20L, Perfil.FISCAL)));
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

    @Test
    void classificarComparaDecisaoCategoriaEPrioridade() {
        demanda(StatusDemanda.EM_TRIAGEM);

        triagem.triar(ator, 5L, new TriagemRequest(DecisaoTriagem.CLASSIFICAR,
                CategoriaFiscalizacao.QUEIMADA_INCENDIO, Prioridade.ALTA, "Ok."));

        verify(governanca).registrarDecisao(GovernancaIaService.AGENTE_TRIAGEM, 5L, 10L, "QUEIMADA_INCENDIO",
                Map.of("decisao", "CLASSIFICAR", "categoria", "QUEIMADA_INCENDIO", "prioridade", "ALTA"),
                CamposAvaliacao.DECISIVO_TRIAGEM);
    }

    @Test
    void complementacaoComparaSoADecisao() {
        demanda(StatusDemanda.EM_TRIAGEM);

        triagem.triar(ator, 5L, new TriagemRequest(DecisaoTriagem.SOLICITAR_COMPLEMENTACAO,
                CategoriaFiscalizacao.QUEIMADA_INCENDIO, Prioridade.ALTA, "Falta a coordenada."));

        verify(governanca).registrarDecisao(eq(GovernancaIaService.AGENTE_TRIAGEM), eq(5L), eq(10L), isNull(),
                eq(Map.of("decisao", "SOLICITAR_COMPLEMENTACAO")), eq(CamposAvaliacao.DECISIVO_TRIAGEM));
    }

    @Test
    void distribuirComparaEquipeFiscalEPrazo() {
        Demanda d = demanda(StatusDemanda.AGUARDANDO_DISTRIBUICAO);
        d.setCategoria(CategoriaFiscalizacao.PESCA_IRREGULAR);
        LocalDate prazo = LocalDate.of(2026, 11, 20);

        distribuicao.distribuir(ator, 5L, new DistribuicaoRequest(1L, 20L, prazo, null));

        verify(governanca).registrarDecisao(GovernancaIaService.AGENTE_DISTRIBUICAO, 5L, 10L, "PESCA_IRREGULAR",
                Map.of("equipeId", "1", "fiscalId", "20", "prazo", "2026-11-20"), null);
    }

    @Test
    void falhaDaGovernancaNaoImpedeATriagem() {
        Demanda d = demanda(StatusDemanda.EM_TRIAGEM);
        when(governanca.registrarDecisao(anyString(), anyLong(), anyLong(), any(), anyMap(), any()))
                .thenReturn(null);

        triagem.triar(ator, 5L, new TriagemRequest(DecisaoTriagem.CLASSIFICAR,
                CategoriaFiscalizacao.QUEIMADA_INCENDIO, Prioridade.ALTA, "Ok."));

        assertThat(d.getStatus()).isEqualTo(StatusDemanda.AGUARDANDO_DISTRIBUICAO);
    }
}
