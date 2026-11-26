package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaFiltro;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaResumo;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemandaServiceTest {

    /** 11/11/2026 15:00 UTC = 11:00 em Cuiabá (UTC-4). */
    static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC);
    static final LocalDate HOJE = LocalDate.of(2026, 11, 11);
    static final DemandaFiltro SEM_FILTRO = new DemandaFiltro(null, null, null, null, null, null, null, null, null, null);

    @Mock DemandaRepository demandas;
    @Mock DemandaHistoricoRepository historicos;
    @Mock FiscalizacaoRepository fiscalizacoes;
    @Mock MunicipioRepository municipios;
    @Mock EquipeRepository equipes;
    @Mock UsuarioRepository usuarios;
    @Mock NumeracaoService numeracao;
    @Mock AuditService auditoria;

    DemandaService service;
    Municipio cuiaba;
    Usuario analista;
    UsuarioAutenticado ator;

    @BeforeEach
    void preparar() {
        service = new DemandaService(demandas, historicos, fiscalizacoes, municipios, equipes, usuarios, numeracao,
                auditoria, RELOGIO, mock(br.com.nexusbuilderlab.semanexus.core.notificacao.NotificacaoService.class));
        cuiaba = mock(Municipio.class);
        when(cuiaba.getId()).thenReturn(5103403);
        when(cuiaba.getNome()).thenReturn("Cuiabá");
        when(cuiaba.getBioma()).thenReturn(Bioma.CERRADO);
        when(municipios.findById(5103403)).thenReturn(Optional.of(cuiaba));
        analista = usuario(10L, Perfil.ANALISTA);
        ator = UsuarioAutenticado.de(analista);
        when(usuarios.findById(10L)).thenReturn(Optional.of(analista));
        when(numeracao.proximoNumero(2026)).thenReturn("DEM-2026/0001");
    }

    private static Usuario usuario(long id, Perfil perfil) {
        Usuario u = new Usuario("Usuário " + id, "u" + id + "@exemplo.test", "hash", perfil);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static DemandaRequest pedido(Integer municipio, LocalDate recebimento, LocalDate prazo) {
        return new DemandaRequest("Título da demanda", "Descrição fictícia", Origem.DENUNCIA, null, municipio, null,
                null, null, null, null, recebimento, null, prazo, null);
    }

    private Demanda demanda(long id, StatusDemanda status) {
        Demanda d = new Demanda("DEM-2026/0001", analista);
        ReflectionTestUtils.setField(d, "id", id);
        d.setStatus(status);
        d.setTitulo("Título da demanda");
        d.setDescricao("Descrição fictícia");
        d.setOrigem(Origem.DENUNCIA);
        d.setMunicipio(cuiaba);
        d.setDataRecebimento(HOJE.minusDays(2));
        return d;
    }

    @Test
    @SuppressWarnings("unchecked")
    void proximasFiltraPorHaversineEOrdena() {
        Demanda perto = comCoordenada(11L, "-15.6000", "-56.1000");
        Demanda medio = comCoordenada(12L, "-15.6300", "-56.1000");   // cerca de 3,3 km
        Demanda longe = comCoordenada(13L, "-15.7000", "-56.1000");   // cerca de 11 km: fora do raio de 10 km
        when(demandas.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(longe, medio, perto));
        var r = service.proximas(ator, -15.6, -56.1, 10, 1L, 8);
        assertThat(r).extracting(p -> p.demanda().id()).containsExactly(11L, 12L);
        assertThat(r.get(1).distanciaKm()).isBetween(3.2, 3.4);
    }

    @Test
    @SuppressWarnings("unchecked")
    void proximasDoFiscalConsultaSuasEquipes() {
        UsuarioAutenticado fiscal = UsuarioAutenticado.de(usuario(20L, Perfil.FISCAL));
        when(demandas.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of());
        assertThat(service.proximas(fiscal, -15.6, -56.1, 5, null, 8)).isEmpty();
        verify(equipes).findIdsByMembro(20L);
    }

    private Demanda comCoordenada(long id, String lat, String lon) {
        Demanda d = demanda(id, StatusDemanda.PENDENTE);
        d.setLatitude(new java.math.BigDecimal(lat));
        d.setLongitude(new java.math.BigDecimal(lon));
        return d;
    }

    private Equipe equipe(long id) {
        Equipe e = new Equipe("Equipe " + id, mock(Setor.class));
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    // ---------------------------------------------------------------- cadastro

    @Test
    void cadastroValidoGravaPendenteComNumeroHistoricoEAuditoria() {
        DemandaDetalhe r = service.cadastrar(ator, pedido(5103403, HOJE, HOJE.plusDays(5)));

        assertThat(r.numero()).isEqualTo("DEM-2026/0001");
        assertThat(r.status()).isEqualTo(StatusDemanda.PENDENTE);
        ArgumentCaptor<DemandaHistorico> h = ArgumentCaptor.forClass(DemandaHistorico.class);
        verify(historicos).save(h.capture());
        assertThat(h.getValue().getTipo()).isEqualTo(TipoHistorico.CADASTRO);
        assertThat(h.getValue().getStatusNovo()).isEqualTo(StatusDemanda.PENDENTE);
        assertThat(h.getValue().getUsuario()).isSameAs(analista);
        verify(auditoria).registrar(eq("CRIAR"), eq("DEMANDA"), any(), anyString());
    }

    @Test
    void detalheExpoePrazoSugeridoNoCadastroEnullQuandoAusente() {
        assertThat(service.cadastrar(ator, pedido(5103403, HOJE, HOJE.plusDays(5))).prazoSugerido())
                .isEqualTo(HOJE.plusDays(5));
        assertThat(service.cadastrar(ator, pedido(5103403, HOJE, null)).prazoSugerido()).isNull();
    }

    @Test
    void detalheExpoePrazoSugeridoNaObtencaoEnullQuandoAusente() {
        Demanda comPrazo = demanda(1, StatusDemanda.PENDENTE);
        comPrazo.setPrazoSugerido(HOJE.plusDays(10));
        Demanda semPrazo = demanda(2, StatusDemanda.PENDENTE);
        when(demandas.buscarDetalhe(1L)).thenReturn(Optional.of(comPrazo));
        when(demandas.buscarDetalhe(2L)).thenReturn(Optional.of(semPrazo));

        assertThat(service.detalhar(ator, 1L).prazoSugerido()).isEqualTo(HOJE.plusDays(10));
        assertThat(service.detalhar(ator, 2L).prazoSugerido()).isNull();
    }

    @Test
    void cadastroUsaOAnoDeHojeEmCuiabaParaANumeracao() {
        // 02:00 UTC de 01/01/2027 ainda é 31/12/2026 em Cuiabá
        Clock virada = Clock.fixed(Instant.parse("2027-01-01T02:00:00Z"), ZoneOffset.UTC);
        DemandaService s = new DemandaService(demandas, historicos, fiscalizacoes, municipios, equipes, usuarios,
                numeracao, auditoria, virada, mock(br.com.nexusbuilderlab.semanexus.core.notificacao.NotificacaoService.class));
        s.cadastrar(ator, pedido(5103403, LocalDate.of(2026, 12, 30), null));
        verify(numeracao).proximoNumero(2026);
    }

    @Test
    void municipioForaDeMtOuInexistenteGeraErroNoCampoMunicipioId() {
        when(municipios.findById(3550308)).thenReturn(Optional.empty());
        DadosInvalidosException e = catchThrowableOfType(DadosInvalidosException.class,
                () -> service.cadastrar(ator, pedido(3550308, HOJE, null)));
        assertThat(e.getErros()).extracting("campo").containsExactly("municipioId");
        verify(demandas, never()).save(any());
        verify(auditoria, never()).registrar(anyString(), anyString(), any(), any());
        verify(auditoria, never()).registrarAlteracao(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void coordenadaIncompletaGeraErroNoCampoFaltante() {
        DemandaRequest p = new DemandaRequest("Título da demanda", "Descrição", Origem.DENUNCIA, null, 5103403, null,
                new BigDecimal("-15.6"), null, null, null, HOJE, null, null, null);
        DadosInvalidosException e = catchThrowableOfType(DadosInvalidosException.class, () -> service.cadastrar(ator, p));
        assertThat(e.getErros()).extracting("campo").containsExactly("longitude");
    }

    @Test
    void dataDeRecebimentoFuturaEPrazoAnteriorAoRecebimentoGeramErrosPorCampo() {
        DadosInvalidosException futura = catchThrowableOfType(DadosInvalidosException.class,
                () -> service.cadastrar(ator, pedido(5103403, HOJE.plusDays(1), null)));
        assertThat(futura.getErros()).extracting("campo").containsExactly("dataRecebimento");

        DadosInvalidosException prazo = catchThrowableOfType(DadosInvalidosException.class,
                () -> service.cadastrar(ator, pedido(5103403, HOJE, HOJE.minusDays(1))));
        assertThat(prazo.getErros()).extracting("campo").containsExactly("prazoSugerido");
    }

    @Test
    void multiplosErrosVoltamJuntos() {
        when(municipios.findById(1)).thenReturn(Optional.empty());
        DadosInvalidosException todos = catchThrowableOfType(DadosInvalidosException.class,
                () -> service.cadastrar(ator, pedido(1, HOJE.plusDays(2), HOJE)));
        assertThat(todos.getErros()).extracting("campo")
                .containsExactlyInAnyOrder("municipioId", "dataRecebimento", "prazoSugerido");
    }

    // ---------------------------------------------------------------- área afetada (#143)

    private static java.util.Map<String, Object> quadrado(double lon0, double lat0, double lon1, double lat1) {
        return br.com.nexusbuilderlab.semanexus.common.geo.GeometriaAreaTest.poligono(
                br.com.nexusbuilderlab.semanexus.common.geo.GeometriaAreaTest.anel(lon0, lat0, lon1, lat1));
    }

    private DemandaRequest comArea(BigDecimal ha, java.util.Map<String, Object> geometria, BigDecimal lat, BigDecimal lon,
                                   LocalDate prazoSugerido) {
        return new DemandaRequest("Título da demanda", "Descrição fictícia", Origem.DENUNCIA, null, 5103403, null,
                lat, lon, ha, geometria, HOJE.minusDays(2), null, prazoSugerido, null);
    }

    @Test
    void cadastroComGeometriaCalculaAreaECentroideEDevolvePrazoSugerido() {
        DemandaDetalhe r = service.cadastrar(ator, comArea(null, quadrado(-56.1, -15.6, -56.0, -15.5), null, null, HOJE.plusDays(10)));
        assertThat(r.areaHa().doubleValue()).isBetween(11_500.0, 12_300.0);
        assertThat(r.areaGeometria()).containsEntry("type", "Polygon");
        assertThat(r.latitude().doubleValue()).isBetween(-15.6, -15.5);
        assertThat(r.longitude().doubleValue()).isBetween(-56.1, -56.0);
        assertThat(r.prazoSugerido()).isEqualTo(HOJE.plusDays(10));
    }

    @Test
    void areaInformadaDivergenteDaCalculadaResponde400() {
        DadosInvalidosException e = catchThrowableOfType(DadosInvalidosException.class,
                () -> service.cadastrar(ator, comArea(new BigDecimal("500"), quadrado(-56.1, -15.6, -56.0, -15.5), null, null, null)));
        assertThat(e.getErros()).extracting("campo").containsExactly("areaHa");
    }

    @Test
    void geometriaForaDeMtResponde400ESemGeometriaAreaInformadaEGravada() {
        DadosInvalidosException e = catchThrowableOfType(DadosInvalidosException.class,
                () -> service.cadastrar(ator, comArea(null, quadrado(-70, -15.6, -69.9, -15.5), null, null, null)));
        assertThat(e.getErros()).extracting("campo").containsExactly("areaGeometria");
        assertThat(service.cadastrar(ator, comArea(new BigDecimal("12.50"), null, null, null, null)).areaHa())
                .isEqualByComparingTo("12.50");
    }

    @Test
    void edicaoComNovaAreaRegistraNoHistoricoEPrazoSugeridoEditavel() {
        Demanda d = demanda(1, StatusDemanda.PENDENTE);
        when(demandas.buscarParaEscrita(1L)).thenReturn(Optional.of(d));
        DemandaDetalhe r = service.editar(ator, 1L, comArea(null, quadrado(-56.1, -15.6, -56.0, -15.5), null, null, HOJE.plusDays(5)));
        assertThat(r.areaHa()).isNotNull();
        assertThat(r.prazoSugerido()).isEqualTo(HOJE.plusDays(5));
        ArgumentCaptor<DemandaHistorico> h = ArgumentCaptor.forClass(DemandaHistorico.class);
        verify(historicos).save(h.capture());
        assertThat(h.getValue().getTexto()).contains("área afetada").contains("prazo sugerido");
    }

    @Test
    void edicaoSemAreaMantemAExistenteEFlagRemove() {
        Demanda d = demanda(1, StatusDemanda.PENDENTE);
        when(demandas.buscarParaEscrita(1L)).thenReturn(Optional.of(d));
        service.editar(ator, 1L, comArea(null, quadrado(-56.1, -15.6, -56.0, -15.5), null, null, null));
        BigDecimal ha = d.getAreaHa();
        String geo = d.getAreaGeometria();
        assertThat(ha).isNotNull();

        DemandaDetalhe mantida = service.editar(ator, 1L, comArea(null, null, null, null, null));
        assertThat(mantida.areaHa()).isEqualByComparingTo(ha);
        assertThat(d.getAreaGeometria()).isEqualTo(geo);

        DemandaDetalhe nova = service.editar(ator, 1L, comArea(null, quadrado(-56.1, -15.6, -56.05, -15.55), null, null, null));
        assertThat(nova.areaHa()).isLessThan(ha);

        DemandaDetalhe removida = service.editar(ator, 1L, new DemandaRequest("Título da demanda", "Descrição fictícia",
                Origem.DENUNCIA, null, 5103403, null, null, null, null, null, HOJE.minusDays(2), null, null, null, true));
        assertThat(removida.areaHa()).isNull();
        assertThat(removida.areaGeometria()).isNull();

        var alteracoes = ArgumentCaptor.forClass(br.com.nexusbuilderlab.semanexus.core.auditoria.Alteracoes.class);
        verify(auditoria, times(4)).registrarAlteracao(eq("EDITAR"), eq("DEMANDA"), eq("1"), anyString(),
                alteracoes.capture());
        var criadaAlt = alteracoes.getAllValues().get(0);
        assertThat(criadaAlt.antes()).containsEntry("areaHa", null).containsEntry("areaGeometria", null);
        assertThat(criadaAlt.depois().get("areaHa")).isNotBlank();
        assertThat(criadaAlt.depois().get("areaGeometria")).startsWith("Polygon (");

        var mantidaAlt = alteracoes.getAllValues().get(1);
        assertThat(mantidaAlt.antes()).doesNotContainKeys("areaHa", "areaGeometria");
        assertThat(mantidaAlt.depois()).doesNotContainKeys("areaHa", "areaGeometria");

        var editadaAlt = alteracoes.getAllValues().get(2);
        assertThat(editadaAlt.antes().get("areaHa")).isNotEqualTo(editadaAlt.depois().get("areaHa"));
        assertThat(editadaAlt.antes().get("areaGeometria")).isNotEqualTo(editadaAlt.depois().get("areaGeometria"));

        var removidaAlt = alteracoes.getAllValues().get(3);
        assertThat(removidaAlt.antes().get("areaHa")).isNotBlank();
        assertThat(removidaAlt.antes().get("areaGeometria")).startsWith("Polygon (");
        assertThat(removidaAlt.depois()).containsEntry("areaHa", null).containsEntry("areaGeometria", null);
    }

    // ---------------------------------------------------------------- edição

    @Test
    void edicaoEmPendenteOuEmTriagemGravaHistoricoEAuditoria() {
        for (StatusDemanda status : List.of(StatusDemanda.PENDENTE, StatusDemanda.EM_TRIAGEM)) {
            Demanda d = demanda(1, status);
            when(demandas.buscarParaEscrita(1L)).thenReturn(Optional.of(d));

            DemandaDetalhe r = service.editar(ator, 1L, new DemandaRequest("Novo título", "Descrição fictícia",
                    Origem.DENUNCIA, null, 5103403, null, null, null, null, null, HOJE.minusDays(2), null, null, null));
            assertThat(r.titulo()).isEqualTo("Novo título");
            assertThat(r.status()).isEqualTo(status);
        }
        ArgumentCaptor<DemandaHistorico> h = ArgumentCaptor.forClass(DemandaHistorico.class);
        verify(historicos, times(2)).save(h.capture());
        assertThat(h.getAllValues()).allSatisfy(x -> {
            assertThat(x.getTipo()).isEqualTo(TipoHistorico.EDICAO);
            assertThat(x.getTexto()).contains("título");
        });
        var alteracoes = ArgumentCaptor.forClass(br.com.nexusbuilderlab.semanexus.core.auditoria.Alteracoes.class);
        verify(auditoria, times(2)).registrarAlteracao(eq("EDITAR"), eq("DEMANDA"), any(), anyString(),
                alteracoes.capture());
        assertThat(alteracoes.getValue().depois()).containsEntry("titulo", "Novo título");
        assertThat(alteracoes.getValue().antes()).containsKey("titulo").doesNotContainKey("origem");
    }

    @Test
    void edicaoSemMudancasNaoGravaHistoricoNemAuditoria() {
        when(demandas.buscarParaEscrita(1L)).thenReturn(Optional.of(demanda(1, StatusDemanda.PENDENTE)));
        service.editar(ator, 1L, new DemandaRequest("Título da demanda", "Descrição fictícia", Origem.DENUNCIA, null,
                5103403, null, null, null, null, null, HOJE.minusDays(2), null, null, null));
        verify(historicos, never()).save(any());
        verify(auditoria, never()).registrar(anyString(), anyString(), any(), any());
    }

    @Test
    void edicaoForaDePendenteOuEmTriagemResponde409ESemEfeitos() {
        for (StatusDemanda status : List.of(StatusDemanda.AGUARDANDO_DISTRIBUICAO, StatusDemanda.DISTRIBUIDA,
                StatusDemanda.EM_ANDAMENTO, StatusDemanda.AGUARDANDO_CONCLUSAO, StatusDemanda.FINALIZADA)) {
            when(demandas.buscarParaEscrita(1L)).thenReturn(Optional.of(demanda(1, status)));
            assertThatThrownBy(() -> service.editar(ator, 1L, pedido(5103403, HOJE, null)))
                    .isInstanceOf(ConflitoException.class);
        }
        verify(historicos, never()).save(any());
        verify(auditoria, never()).registrar(anyString(), anyString(), any(), any());
    }

    @Test
    void edicaoEExclusaoUsamLockPessimistaNaLeitura() {
        when(demandas.buscarParaEscrita(1L)).thenReturn(Optional.of(demanda(1, StatusDemanda.PENDENTE)));
        service.editar(ator, 1L, pedido(5103403, HOJE, null));
        service.excluir(ator, 1L);
        verify(demandas, times(2)).buscarParaEscrita(1L);
        verify(demandas, never()).buscarDetalhe(any());
    }

    @Test
    void edicaoDeDemandaInexistenteResponde404() {
        when(demandas.buscarParaEscrita(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.editar(ator, 99L, pedido(5103403, HOJE, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ---------------------------------------------------------------- exclusão

    @Test
    void exclusaoEmPendenteMarcaExclusaoNoFusoDeCuiabaEGravaHistoricoEAuditoria() {
        Demanda d = demanda(1, StatusDemanda.PENDENTE);
        when(demandas.buscarParaEscrita(1L)).thenReturn(Optional.of(d));

        service.excluir(ator, 1L);

        assertThat((LocalDateTime) ReflectionTestUtils.getField(d, "excluidoEm"))
                .isEqualTo(LocalDateTime.of(2026, 11, 11, 11, 0));
        verify(demandas).save(d);
        ArgumentCaptor<DemandaHistorico> h = ArgumentCaptor.forClass(DemandaHistorico.class);
        verify(historicos).save(h.capture());
        assertThat(h.getValue().getTipo()).isEqualTo(TipoHistorico.EXCLUSAO);
        verify(auditoria).registrar(eq("EXCLUIR"), eq("DEMANDA"), eq("1"), anyString());
    }

    @Test
    void exclusaoForaDePendenteResponde409() {
        for (StatusDemanda status : StatusDemanda.values()) {
            if (status == StatusDemanda.PENDENTE) {
                continue;
            }
            Demanda d = demanda(1, status);
            when(demandas.buscarParaEscrita(1L)).thenReturn(Optional.of(d));
            assertThatThrownBy(() -> service.excluir(ator, 1L)).isInstanceOf(ConflitoException.class);
            assertThat(ReflectionTestUtils.getField(d, "excluidoEm")).isNull();
        }
        verify(historicos, never()).save(any());
        verify(auditoria, never()).registrar(anyString(), anyString(), any(), any());
    }

    @Test
    void detalheExpoeAreaHaEPoligonoQuandoRegistradosENullSemArea() {
        Demanda comArea = demanda(1, StatusDemanda.PENDENTE);
        ReflectionTestUtils.setField(comArea, "areaHa", new java.math.BigDecimal("42.35"));
        ReflectionTestUtils.setField(comArea, "areaGeometria",
                "{\"type\":\"Polygon\",\"coordinates\":[[[-56.1,-15.6],[-56.0,-15.6],[-56.0,-15.5],[-56.1,-15.6]]]}");
        Demanda semArea = demanda(2, StatusDemanda.PENDENTE);
        when(demandas.buscarDetalhe(1L)).thenReturn(Optional.of(comArea));
        when(demandas.buscarDetalhe(2L)).thenReturn(Optional.of(semArea));

        DemandaDetalhe r = service.detalhar(ator, 1L);
        assertThat(r.areaHa()).isEqualByComparingTo("42.35");
        assertThat(r.areaGeometria()).containsEntry("type", "Polygon").containsKey("coordinates");
        assertThat(service.detalhar(ator, 2L).areaHa()).isNull();
        assertThat(service.detalhar(ator, 2L).areaGeometria()).isNull();
    }

    @Test
    void fiscalForaDoEscopoNaoRecebeAreaDaDemanda() {
        UsuarioAutenticado fiscal = UsuarioAutenticado.de(usuario(20L, Perfil.FISCAL));
        Demanda d = demanda(1, StatusDemanda.DISTRIBUIDA);
        d.setEquipe(equipe(2));
        ReflectionTestUtils.setField(d, "areaHa", new java.math.BigDecimal("10.00"));
        when(demandas.buscarDetalhe(1L)).thenReturn(Optional.of(d));
        when(equipes.findIdsByMembro(20L)).thenReturn(List.of(1L));

        assertThatThrownBy(() -> service.detalhar(fiscal, 1L)).isInstanceOf(AccessDeniedException.class);
    }

    // ---------------------------------------------------------------- escopo FISCAL

    @Test
    void fiscalNaoVeDetalheNemHistoricoDeDemandaDeOutraEquipe() {
        UsuarioAutenticado fiscal = UsuarioAutenticado.de(usuario(20L, Perfil.FISCAL));
        Demanda d = demanda(1, StatusDemanda.DISTRIBUIDA);
        d.setEquipe(equipe(2));
        when(demandas.buscarDetalhe(1L)).thenReturn(Optional.of(d));
        when(equipes.findIdsByMembro(20L)).thenReturn(List.of(1L));

        assertThatThrownBy(() -> service.detalhar(fiscal, 1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.historico(fiscal, 1L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(historicos);
    }

    @Test
    void fiscalVeDemandaDaSuaEquipeOuAtribuidaAEle() {
        Usuario f = usuario(20L, Perfil.FISCAL);
        UsuarioAutenticado fiscal = UsuarioAutenticado.de(f);
        Demanda daEquipe = demanda(1, StatusDemanda.DISTRIBUIDA);
        daEquipe.setEquipe(equipe(1));
        Demanda atribuida = demanda(2, StatusDemanda.EM_ANDAMENTO);
        atribuida.setEquipe(equipe(3));
        atribuida.setFiscal(f);
        when(demandas.buscarDetalhe(1L)).thenReturn(Optional.of(daEquipe));
        when(demandas.buscarDetalhe(2L)).thenReturn(Optional.of(atribuida));
        when(equipes.findIdsByMembro(20L)).thenReturn(List.of(1L));

        assertThat(service.detalhar(fiscal, 1L).id()).isEqualTo(1L);
        assertThat(service.detalhar(fiscal, 2L).id()).isEqualTo(2L);
    }

    @Test
    void fiscalSemEquipeNaoVeDemandaSemAtribuicao() {
        UsuarioAutenticado fiscal = UsuarioAutenticado.de(usuario(20L, Perfil.FISCAL));
        when(demandas.buscarDetalhe(1L)).thenReturn(Optional.of(demanda(1, StatusDemanda.PENDENTE)));
        when(equipes.findIdsByMembro(20L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.detalhar(fiscal, 1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void outrosPerfisVeemQualquerDemanda() {
        Demanda d = demanda(1, StatusDemanda.DISTRIBUIDA);
        d.setEquipe(equipe(2));
        when(demandas.buscarDetalhe(1L)).thenReturn(Optional.of(d));
        for (Perfil p : List.of(Perfil.ADMIN, Perfil.GESTOR, Perfil.ANALISTA, Perfil.CONSULTA)) {
            assertThat(service.detalhar(UsuarioAutenticado.de(usuario(30L, p)), 1L).id()).isEqualTo(1L);
        }
        verify(equipes, never()).findIdsByMembro(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void listagemTrazConcluidaEmSoNasFinalizadasEAtualizadoEm() {
        Demanda finalizada = demanda(1, StatusDemanda.FINALIZADA);
        LocalDateTime fim = LocalDateTime.of(2026, 11, 10, 9, 30);
        finalizada.setConcluidaEm(fim);
        Demanda aberta = demanda(2, StatusDemanda.PENDENTE);
        when(demandas.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Demanda>(List.of(finalizada, aberta), PageRequest.of(0, 10), 2));

        List<DemandaResumo> itens = service.listar(ator, SEM_FILTRO, PageRequest.of(0, 10)).content();

        assertThat(itens.get(0).concluidaEm()).isEqualTo(fim);
        assertThat(itens.get(1).concluidaEm()).isNull();
        assertThat(itens.get(0).atualizadoEm()).isEqualTo(finalizada.getAtualizadoEm());
    }

    @SuppressWarnings("unchecked")
    @Test
    void listagemDoFiscalConsultaAsEquipesDoFiscalEOutrosPerfisNao() {
        when(demandas.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Demanda>(List.of(demanda(1, StatusDemanda.PENDENTE)), PageRequest.of(0, 10), 1));
        when(equipes.findIdsByMembro(20L)).thenReturn(List.of(1L));

        PageResponse<DemandaResumo> r = service.listar(UsuarioAutenticado.de(usuario(20L, Perfil.FISCAL)), SEM_FILTRO,
                PageRequest.of(0, 10));
        assertThat(r.content()).hasSize(1);
        verify(equipes).findIdsByMembro(20L);

        service.listar(ator, SEM_FILTRO, PageRequest.of(0, 10));
        verify(equipes, times(1)).findIdsByMembro(any());
    }

    // ---------------------------------------------------------------- listagem: validações e ordenação

    @Test
    void ordenacaoPorCampoForaDaWhitelistResponde400ComCampoSort() {
        DadosInvalidosException e = catchThrowableOfType(DadosInvalidosException.class,
                () -> service.listar(ator, SEM_FILTRO, PageRequest.of(0, 10, Sort.by("criadoPor.senhaHash"))));
        assertThat(e.getErros()).extracting("campo").containsExactly("sort");
        verifyNoInteractions(demandas);
    }

    @SuppressWarnings("unchecked")
    @Test
    void ordenacaoPorCampoPermitidoEAceita() {
        when(demandas.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        for (String campo : DemandaSpecification.CAMPOS_ORDENAVEIS) {
            assertThat(service.listar(ator, SEM_FILTRO, PageRequest.of(0, 10, Sort.by(campo))).content()).isEmpty();
        }
    }

    @Test
    void periodoInvertidoResponde400ComCampoDe() {
        DemandaFiltro f = new DemandaFiltro(null, null, null, null, null, null, null, null, HOJE, HOJE.minusDays(1));
        DadosInvalidosException e = catchThrowableOfType(DadosInvalidosException.class,
                () -> service.listar(ator, f, PageRequest.of(0, 10)));
        assertThat(e.getErros()).extracting("campo").containsExactly("de");
    }

    @Test
    void atrasadaEDerivadaDoPrazoEDoStatus() {
        Demanda d = demanda(1, StatusDemanda.EM_ANDAMENTO);
        d.setPrazo(HOJE.minusDays(1));
        assertThat(d.atrasada(HOJE)).isTrue();
        d.setStatus(StatusDemanda.FINALIZADA);
        assertThat(d.atrasada(HOJE)).isFalse();
        d.setStatus(StatusDemanda.EM_ANDAMENTO);
        d.setPrazo(HOJE);
        assertThat(d.atrasada(HOJE)).isFalse();
    }
}
