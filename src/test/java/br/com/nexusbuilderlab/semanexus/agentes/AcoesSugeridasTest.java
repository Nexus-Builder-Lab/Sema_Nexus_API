package br.com.nexusbuilderlab.semanexus.agentes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import br.com.nexusbuilderlab.semanexus.agentes.AcaoSugerida.Tipo;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.docia.documento.Documento;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import br.com.nexusbuilderlab.semanexus.flux.agente.SugestaoTriagem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class AcoesSugeridasTest {

    DocumentoRepository docs = mock(DocumentoRepository.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    DemandaService demandas = mock(DemandaService.class);
    AcoesSugeridas acoes = new AcoesSugeridas(docs, jdbc, demandas);
    UsuarioAutenticado fiscal = new UsuarioAutenticado(5L, "F", "f@x", Perfil.FISCAL);
    UsuarioAutenticado consulta = new UsuarioAutenticado(6L, "C", "c@x", Perfil.CONSULTA);

    Documento doc(long id, String titulo, String tipo) {
        Documento d = new Documento(titulo, null, "Parecer técnico", tipo, LocalDate.of(2026, 10, 1), null, "k",
                "a.pdf", "application/pdf", 1, "s", "h", "texto do documento", null);
        ReflectionTestUtils.setField(d, "id", id);
        when(docs.buscarAtivo(id)).thenReturn(Optional.of(d));
        return d;
    }

    DemandaDetalhe demanda(long id) {
        DemandaDetalhe d = mock(DemandaDetalhe.class);
        when(d.id()).thenReturn(id);
        when(d.numero()).thenReturn("DEM-2026/00" + id);
        when(demandas.detalhar(any(), org.mockito.ArgumentMatchers.eq(id))).thenReturn(d);
        return d;
    }

    @BeforeEach
    void naoVinculado() {
        when(jdbc.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Integer.class), any(), any()))
                .thenReturn(0);
    }

    @Test
    void duasVersoesDoMesmoParecerGeramComparacao() {
        doc(7, "Parecer Técnico 12 (versão 2)", "Parecer");
        doc(3, "Parecer Técnico 12 (versão 1)", "Parecer");
        List<AcaoSugerida> r = acoes.paraDocia(fiscal, null, List.of(7L, 3L));
        assertThat(r).hasSize(1);
        assertThat(r.get(0).tipo()).isEqualTo(Tipo.ABRIR_COMPARACAO);
        assertThat(r.get(0).parametros()).containsEntry("aId", 3L).containsEntry("bId", 7L);
    }

    @Test
    void documentosDiferentesNaoGeramComparacaoEIdInexistenteEhIgnorado() {
        doc(1, "Parecer A", "Parecer");
        doc(2, "Relatório B", "Relatório");
        when(docs.buscarAtivo(999L)).thenReturn(Optional.empty());
        assertThat(acoes.paraDocia(fiscal, null, List.of(1L, 2L, 999L))).isEmpty();
    }

    @Test
    void vinculoSugeridoSoParaQuemPodeVincularEComDemandaAcessivel() {
        doc(1, "Parecer A", "Parecer");
        demanda(10);
        List<AcaoSugerida> r = acoes.paraDocia(fiscal, 10L, List.of(1L));
        assertThat(r).extracting(AcaiSugeridaTipo::of).containsExactly(Tipo.SUGERIR_VINCULO_DEMANDA);
        assertThat(r.get(0).parametros()).containsEntry("documentoId", 1L).containsEntry("demandaId", 10L);
        // CONSULTA não vincula documentos
        assertThat(acoes.paraDocia(consulta, 10L, List.of(1L))).isEmpty();
        // demanda fora do escopo do FISCAL ou inexistente: descartada
        when(demandas.detalhar(any(), org.mockito.ArgumentMatchers.eq(11L))).thenThrow(new AccessDeniedException("x"));
        when(demandas.detalhar(any(), org.mockito.ArgumentMatchers.eq(12L)))
                .thenThrow(new RecursoNaoEncontradoException("x"));
        assertThat(acoes.paraDocia(fiscal, 11L, List.of(1L))).isEmpty();
        assertThat(acoes.paraDocia(fiscal, 12L, List.of(1L))).isEmpty();
    }

    @Test
    void documentoJaVinculadoNaoGeraVinculo() {
        doc(1, "Parecer A", "Parecer");
        demanda(10);
        when(jdbc.queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Integer.class), any(), any()))
                .thenReturn(1);
        assertThat(acoes.paraDocia(fiscal, 10L, List.of(1L))).isEmpty();
    }

    @Test
    void triagemDerivaDuplicatasAcessiveisEMapaDescartandoInacessiveisEInventadas() {
        DemandaDetalhe propria = demanda(1);
        demanda(2);
        when(demandas.detalhar(any(), org.mockito.ArgumentMatchers.eq(3L))).thenThrow(new AccessDeniedException("x"));
        when(demandas.detalhar(any(), org.mockito.ArgumentMatchers.eq(99L)))
                .thenThrow(new RecursoNaoEncontradoException("x"));
        var dups = List.of(dup(2L), dup(3L), dup(99L), dup(1L), dup(2L));
        var esp = new SugestaoTriagem.EvidenciaEspacial("COORDENADA", -12.5, -55.7, 10, null, List.of(), List.of());
        List<AcaoSugerida> r = acoes.paraTriagem(fiscal, propria, dups, esp);
        assertThat(r).extracting(AcaoSugerida::tipo).containsExactly(Tipo.ABRIR_DEMANDA, Tipo.VER_NO_MAPA);
        assertThat(r.get(0).parametros()).containsEntry("demandaId", 2L);
        assertThat(r.get(1).parametros()).containsEntry("lat", -12.5).containsEntry("lon", -55.7)
                .containsEntry("demandaId", 1L).containsKey("zoom");
        // fallback ao município: sem ponto exato, sem VER_NO_MAPA
        var mun = new SugestaoTriagem.EvidenciaEspacial("MUNICIPIO", -12.5, -55.7, 10, null, List.of(), List.of());
        assertThat(acoes.paraTriagem(fiscal, propria, List.of(), mun)).isEmpty();
    }

    @Test
    void derivacaoSoLeNadaEhGravado() {
        doc(1, "Parecer A", "Parecer");
        demanda(10);
        acoes.paraDocia(fiscal, 10L, List.of(1L));
        verify(jdbc).queryForObject(any(String.class), org.mockito.ArgumentMatchers.eq(Integer.class), any(), any());
        verifyNoMoreInteractions(jdbc); // nenhum update/insert
    }

    private static SugestaoTriagem.Duplicata dup(Long id) {
        return new SugestaoTriagem.Duplicata(id, "N", "T", null, null, 1.0);
    }

    static final class AcaiSugeridaTipo {
        static Tipo of(AcaoSugerida a) { return a.tipo(); }
    }
}
