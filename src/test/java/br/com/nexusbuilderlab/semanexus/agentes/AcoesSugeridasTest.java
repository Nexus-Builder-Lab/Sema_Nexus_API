package br.com.nexusbuilderlab.semanexus.agentes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

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

    java.util.List<Documento> existentes = new java.util.ArrayList<>();
    java.util.Map<String, Long> demandasPorNumero = new java.util.HashMap<>();
    java.util.List<String> vinculados = new java.util.ArrayList<>();

    Documento doc(long id, String titulo, String tipo) {
        return doc(id, titulo, tipo, "texto do documento");
    }

    Documento doc(long id, String titulo, String tipo, String texto) {
        Documento d = new Documento(titulo, null, "Parecer técnico", tipo, LocalDate.of(2026, 10, 1), null, "k",
                "a.pdf", "application/pdf", 1, "s", "h", texto, null);
        ReflectionTestUtils.setField(d, "id", id);
        existentes.add(d);
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
    @SuppressWarnings("unchecked")
    void preparar() {
        when(docs.findAllById(any())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            return existentes.stream().filter(d -> ids.contains(d.getId())).toList();
        });
        when(jdbc.queryForList(any(String.class), any(Object[].class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("FROM DEMANDA WHERE")) {
                return demandasPorNumero.entrySet().stream()
                        .<java.util.Map<String, Object>>map(e -> java.util.Map.of("NUMERO", e.getKey(), "ID", e.getValue()))
                        .toList();
            }
            return vinculados.stream().<java.util.Map<String, Object>>map(v -> java.util.Map.of(
                    "DOCUMENTO_ID", Long.parseLong(v.split(":")[0]), "DEMANDA_ID", Long.parseLong(v.split(":")[1])))
                    .toList();
        });
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
    void documentosDiferentesOuTituloGenericoNaoGeramComparacao() {
        doc(1, "Parecer A", "Parecer");
        doc(2, "Relatório B", "Relatório");
        assertThat(acoes.paraDocia(fiscal, null, List.of(1L, 2L, 999L))).isEmpty();
        // mesmo tipo/categoria, mas títulos que só diferem por assunto: não são versões
        doc(3, "Parecer Técnico — Queimadas", "Parecer");
        doc(4, "Parecer Técnico — Desmatamento", "Parecer");
        assertThat(acoes.paraDocia(fiscal, null, List.of(3L, 4L))).isEmpty();
    }

    @Test
    void textoComNumeroDeDemandaExistenteAcessivelNaoVinculadaSugereVinculo() {
        doc(1, "Parecer A", "Parecer", "Refere-se à DEM-2026/0010 e à DEM-2026/0011 e à DEM-2026/0012.");
        demandasPorNumero.put("DEM-2026/0010", 10L);
        demandasPorNumero.put("DEM-2026/0011", 11L); // já vinculada
        vinculados.add("1:11");
        demanda(10);
        demanda(11);
        when(demandas.detalhar(any(), org.mockito.ArgumentMatchers.eq(12L))).thenThrow(new AccessDeniedException("x"));
        demandasPorNumero.put("DEM-2026/0012", 12L); // fora do escopo
        List<AcaoSugerida> r = acoes.paraDocia(fiscal, null, List.of(1L));
        assertThat(r).hasSize(1);
        assertThat(r.get(0).tipo()).isEqualTo(Tipo.SUGERIR_VINCULO_DEMANDA);
        assertThat(r.get(0).parametros()).containsEntry("documentoId", 1L).containsEntry("demandaId", 10L);
        // CONSULTA não vincula; demanda inexistente (numero sem registro) não gera ação
        assertThat(acoes.paraDocia(consulta, null, List.of(1L))).isEmpty();
        doc(2, "Parecer B", "Parecer", "Cita DEM-2026/9999.");
        assertThat(acoes.paraDocia(fiscal, null, List.of(2L))).isEmpty();
    }

    @Test
    void limitaAoMaximoDeAcoes() {
        StringBuilder t = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            t.append(" DEM-2026/000").append(i);
            demandasPorNumero.put("DEM-2026/000" + i, (long) i);
            demanda(i);
        }
        doc(1, "A", "Parecer", t.toString());
        doc(2, "B", "Parecer", t.toString());
        assertThat(acoes.paraDocia(fiscal, null, List.of(1L, 2L))).hasSize(AcoesSugeridas.MAX_ACOES);
    }

    @Test
    void mapaRejeitaForaDeMatoGrossoEZeroZero() {
        DemandaDetalhe propria = demanda(1);
        for (double[] c : new double[][] {{0, 0}, {-23.5, -46.6}, {-12.5, -40.0}}) {
            var esp = new SugestaoTriagem.EvidenciaEspacial("COORDENADA", c[0], c[1], 10, null, List.of(), List.of());
            assertThat(acoes.paraTriagem(fiscal, propria, List.of(), esp)).isEmpty();
        }
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
        doc(1, "Parecer A", "Parecer", "DEM-2026/0010");
        demandasPorNumero.put("DEM-2026/0010", 10L);
        demanda(10);
        acoes.paraDocia(fiscal, null, List.of(1L));
        verify(jdbc, org.mockito.Mockito.never()).update(any(String.class), any(Object[].class));
        verify(jdbc, org.mockito.Mockito.never()).batchUpdate(any(String[].class));
    }

    private static SugestaoTriagem.Duplicata dup(Long id) {
        return new SugestaoTriagem.Duplicata(id, "N", "T", null, null, 1.0);
    }

    static final class AcaiSugeridaTipo {
        static Tipo of(AcaoSugerida a) { return a.tipo(); }
    }
}
