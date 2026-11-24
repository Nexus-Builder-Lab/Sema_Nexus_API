package br.com.nexusbuilderlab.semanexus.flux.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import br.com.nexusbuilderlab.semanexus.agentes.AgentesConfiguracao;
import br.com.nexusbuilderlab.semanexus.agentes.ClienteModelo;
import br.com.nexusbuilderlab.semanexus.agentes.ExecutorAgente;
import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DecisaoTriagem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaResumo;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto.PorMunicipioResponse;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteService;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class AgenteTriagemServiceTest {

    private static final int SINOP = 5107909;
    private final ObjectMapper json = new ObjectMapper();
    private final DemandaService demandas = mock(DemandaService.class);
    private final MunicipioRepository municipios = mock(MunicipioRepository.class);
    private final ReferenciaSateliteService referencia = mock(ReferenciaSateliteService.class);
    private final AuditService auditoria = mock(AuditService.class);
    private final UsuarioAutenticado ator = mock(UsuarioAutenticado.class);
    private final AgentesConfiguracao config = new AgentesConfiguracao("ollama", "", "claude-haiku-5-5",
            "claude-sonnet-5-5", "http://ollama:11434", "qwen3:4b", false, 30, 6);

    /** Modelo simulado que executa um roteiro de respostas. */
    static class ModeloRoteiro implements ClienteModelo {
        final Deque<RespostaModelo> respostas = new ArrayDeque<>();
        boolean disponivel = true;

        @Override
        public boolean disponivel() {
            return disponivel;
        }

        @Override
        public String modelo(String sugerido) {
            return "qwen3:4b";
        }

        @Override
        public RespostaModelo criar(PedidoModelo pedido) {
            if (respostas.isEmpty()) {
                throw new IllegalStateException("fim do roteiro");
            }
            return respostas.poll();
        }
    }

    private static DemandaDetalhe demanda(StatusDemanda status) {
        MunicipioRef sinop = new MunicipioRef(SINOP, "Sinop", Bioma.AMAZONIA);
        return new DemandaDetalhe(4L, "DEM-2026/0004", "Descarte irregular de resíduos — Sinop",
                "Denúncia de descarte de lixo e óleo em terreno próximo ao córrego.", Origem.values()[0], null, sinop,
                null, null, null, null, null, LocalDate.now(), null, null, null, status, false, null, null, null, null, null, null,
                null, 0, null, null, null);
    }

    private static DemandaResumo resumo(long id, String numero, CategoriaFiscalizacao cat, StatusDemanda status) {
        return new DemandaResumo(id, numero, "Lixo em córrego", new MunicipioRef(SINOP, "Sinop", Bioma.AMAZONIA),
                Origem.values()[0], cat, Prioridade.MEDIA, status, LocalDate.now(), null, false, null, null, null, null);
    }

    private AgenteTriagemService service(ModeloRoteiro modelo) {
        Municipio m = mock(Municipio.class);
        when(m.getRegiao()).thenReturn("Sinop");
        when(municipios.findById(SINOP)).thenReturn(Optional.of(m));
        when(referencia.porMunicipio(any())).thenReturn(new PorMunicipioResponse("INPE", false, null, "", 12, List.of()));
        when(demandas.listar(eq(ator), any(), any())).thenReturn(new PageResponse<>(List.of(
                resumo(4L, "DEM-2026/0004", null, StatusDemanda.PENDENTE),
                resumo(9L, "DEM-2026/0009", CategoriaFiscalizacao.RESIDUOS_POLUICAO, StatusDemanda.DISTRIBUIDA),
                resumo(10L, "DEM-2026/0010", CategoriaFiscalizacao.RESIDUOS_POLUICAO, StatusDemanda.FINALIZADA)),
                0, 9, 3, 1));
        return new AgenteTriagemService(demandas, municipios, referencia,
                new ExecutorAgente(modelo, config, auditoria, json), config,
                new AnaliseEspacial(demandas, referencia, mock(org.springframework.jdbc.core.JdbcTemplate.class), 90));
    }

    private ClienteModelo.RespostaModelo ferramenta(String nome, String args) throws Exception {
        return new ClienteModelo.RespostaModelo("tool_calls",
                List.of(new ClienteModelo.ChamadaFerramenta("c-" + nome, nome, json.readTree(args))), "", null, 10, 5, 0);
    }

    @Test
    void agenteUsaFerramentasEValidaDuplicatas() throws Exception {
        when(demandas.detalhar(ator, 4L)).thenReturn(demanda(StatusDemanda.EM_TRIAGEM));
        ModeloRoteiro modelo = new ModeloRoteiro();
        modelo.respostas.add(ferramenta("obter_demanda", "{}"));
        modelo.respostas.add(ferramenta("buscar_demandas_semelhantes", "{\"mesmo_municipio\":true}"));
        modelo.respostas.add(ferramenta("alertas_inpe_municipio", "{}"));
        modelo.respostas.add(new ClienteModelo.RespostaModelo("stop", List.of(),
                "{\"decisao\":\"CLASSIFICAR\",\"categoria\":\"RESIDUOS_POLUICAO\",\"prioridade\":\"MEDIA\","
                        + "\"justificativa\":\"Descarte de resíduos relatado.\","
                        + "\"duplicatas\":[\"DEM-2026/0009\",\"DEM-2026/0010\",\"DEM-2099/9999\"]}", null, 50, 30, 0));

        SugestaoTriagem s = service(modelo).sugerir(ator, 4L);

        assertThat(s.modo()).isEqualTo("LOCAL");
        assertThat(s.modelo()).isEqualTo("qwen3:4b");
        assertThat(s.decisao()).isEqualTo(DecisaoTriagem.CLASSIFICAR);
        assertThat(s.categoria()).isEqualTo(CategoriaFiscalizacao.RESIDUOS_POLUICAO);
        assertThat(s.prioridade()).isEqualTo(Prioridade.MEDIA);
        assertThat(s.nucleo().sigla()).isEqualTo("NR-SNO");
        // 0010 está finalizada e 2099/9999 não veio da ferramenta: descartadas
        assertThat(s.duplicatas()).extracting(SugestaoTriagem.Duplicata::numero).containsExactly("DEM-2026/0009");
        assertThat(s.evidencias()).hasSize(3).anyMatch(e -> e.contains("12 foco(s) de calor"));
        assertThat(s.avisos()).isEmpty();
    }

    @Test
    void camposFaltantesSaoCompletadosPelasRegras() throws Exception {
        when(demandas.detalhar(ator, 4L)).thenReturn(demanda(StatusDemanda.PENDENTE));
        ModeloRoteiro modelo = new ModeloRoteiro();
        modelo.respostas.add(new ClienteModelo.RespostaModelo("stop", List.of(),
                "{\"decisao\":\"CLASSIFICAR\",\"categoria\":\"NENHUMA\",\"prioridade\":\"xyz\",\"justificativa\":\"\","
                        + "\"duplicatas\":[]}", null, 50, 30, 0));

        SugestaoTriagem s = service(modelo).sugerir(ator, 4L);

        assertThat(s.categoria()).isEqualTo(CategoriaFiscalizacao.RESIDUOS_POLUICAO);
        assertThat(s.prioridade()).isNotNull();
        assertThat(s.justificativa()).isNotBlank();
        assertThat(s.avisos()).anyMatch(a -> a.contains("Categoria completada"));
    }

    @Test
    void semIaUsaRegrasLocaisEAudita() {
        when(demandas.detalhar(ator, 4L)).thenReturn(demanda(StatusDemanda.PENDENTE));
        ModeloRoteiro modelo = new ModeloRoteiro();
        modelo.disponivel = false;

        SugestaoTriagem s = service(modelo).sugerir(ator, 4L);

        assertThat(s.modo()).isEqualTo("DEMONSTRATIVO");
        assertThat(s.modelo()).isNull();
        assertThat(s.categoria()).isEqualTo(CategoriaFiscalizacao.RESIDUOS_POLUICAO);
        assertThat(s.duplicatas()).extracting(SugestaoTriagem.Duplicata::numero).containsExactly("DEM-2026/0009");
        verify(auditoria).registrar(eq("AGENTE_DEMONSTRATIVO"), eq("AGENTE"), eq("triagem:4"), anyString());
    }

    private static DemandaDetalhe comCoordenada() {
        DemandaDetalhe b = demanda(StatusDemanda.PENDENTE);
        return new DemandaDetalhe(b.id(), b.numero(), b.titulo(), b.descricao(), b.origem(), null, b.municipio(), null,
                java.math.BigDecimal.valueOf(-11.86), java.math.BigDecimal.valueOf(-55.5), null, null, b.dataRecebimento(),
                null, null, null, b.status(), false, null, null, null, null, null, null, null, 0, null, null, null);
    }

    @Test
    void modoDemonstrativoUsaProximidadeEspacialQuandoHaCoordenadas() {
        when(demandas.detalhar(ator, 4L)).thenReturn(comCoordenada());
        when(demandas.proximas(eq(ator), anyDouble(), anyDouble(), eq(10.0), eq(4L), anyInt())).thenReturn(List.of(
                new br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaProxima(
                        resumo(21L, "DEM-2026/0021", null, StatusDemanda.PENDENTE), 4.2)));
        ModeloRoteiro modelo = new ModeloRoteiro();
        modelo.disponivel = false;

        SugestaoTriagem s = service(modelo).sugerir(ator, 4L);

        assertThat(s.modo()).isEqualTo("DEMONSTRATIVO");
        assertThat(s.espacial().referencia()).isEqualTo("COORDENADA");
        assertThat(s.espacial().raioKm()).isEqualTo(10);
        assertThat(s.espacial().demandasProximas()).extracting(SugestaoTriagem.Proxima::distanciaKm).containsExactly(4.2);
        assertThat(s.duplicatas()).extracting(SugestaoTriagem.Duplicata::numero).containsExactly("DEM-2026/0021");
        assertThat(s.duplicatas().get(0).distanciaKm()).isEqualTo(4.2);
        assertThat(s.evidencias()).anyMatch(e -> e.contains("Proximidade espacial") && e.contains("10 km"));
    }

    @Test
    void agenteChamaAsFerramentasGeoespaciais() throws Exception {
        when(demandas.detalhar(ator, 4L)).thenReturn(comCoordenada());
        when(demandas.proximas(eq(ator), anyDouble(), anyDouble(), eq(5.0), eq(4L), anyInt())).thenReturn(List.of(
                new br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaProxima(
                        resumo(9L, "DEM-2026/0009", CategoriaFiscalizacao.RESIDUOS_POLUICAO, StatusDemanda.PENDENTE), 1.5)));
        ModeloRoteiro modelo = new ModeloRoteiro();
        modelo.respostas.add(ferramenta("alertas_inpe_raio_coordenada", "{\"numero\":\"DEM-2026/0004\",\"raio_km\":5}"));
        modelo.respostas.add(ferramenta("demandas_proximas_coordenada", "{\"numero\":\"DEM-2026/0004\",\"raio_km\":5}"));
        modelo.respostas.add(ferramenta("documentos_mesma_localidade", "{\"numero\":\"DEM-2026/0004\"}"));
        modelo.respostas.add(new ClienteModelo.RespostaModelo("stop", List.of(),
                "{\"decisao\":\"CLASSIFICAR\",\"categoria\":\"RESIDUOS_POLUICAO\",\"prioridade\":\"MEDIA\","
                        + "\"justificativa\":\"Descarte de resíduos relatado.\",\"duplicatas\":[\"DEM-2026/0009\"]}",
                null, 50, 30, 0));

        SugestaoTriagem s = service(modelo).sugerir(ator, 4L);

        assertThat(s.modo()).isEqualTo("LOCAL");
        assertThat(s.duplicatas()).extracting(SugestaoTriagem.Duplicata::numero).containsExactly("DEM-2026/0009");
        assertThat(s.avisos()).isEmpty();
        verify(demandas).proximas(eq(ator), anyDouble(), anyDouble(), eq(5.0), eq(4L), anyInt());
    }

    @Test
    void ferramentasReutilizamAAnaliseEspacialDoRaioPadrao() throws Exception {
        when(demandas.detalhar(ator, 4L)).thenReturn(comCoordenada());
        ModeloRoteiro modelo = new ModeloRoteiro();
        modelo.respostas.add(ferramenta("alertas_inpe_raio_coordenada", "{\"numero\":\"DEM-2026/0004\",\"raio_km\":10}"));
        modelo.respostas.add(ferramenta("demandas_proximas_coordenada", "{\"numero\":\"DEM-2026/0004\",\"raio_km\":10}"));
        modelo.respostas.add(ferramenta("documentos_mesma_localidade", "{\"numero\":\"DEM-2026/0004\"}"));
        modelo.respostas.add(new ClienteModelo.RespostaModelo("stop", List.of(),
                "{\"decisao\":\"CLASSIFICAR\",\"categoria\":\"RESIDUOS_POLUICAO\",\"prioridade\":\"MEDIA\","
                        + "\"justificativa\":\"Descarte de resíduos relatado.\",\"duplicatas\":[]}", null, 50, 30, 0));
        service(modelo).sugerir(ator, 4L);
        verify(demandas, org.mockito.Mockito.times(1)).proximas(eq(ator), anyDouble(), anyDouble(), eq(10.0), eq(4L),
                anyInt());
    }

    @Test
    void falhaDaIaCaiNasRegrasComAviso() {
        when(demandas.detalhar(ator, 4L)).thenReturn(demanda(StatusDemanda.PENDENTE));
        ModeloRoteiro modelo = new ModeloRoteiro(); // roteiro vazio: erro na primeira chamada

        SugestaoTriagem s = service(modelo).sugerir(ator, 4L);

        assertThat(s.modo()).isEqualTo("DEMONSTRATIVO");
        assertThat(s.avisos()).anyMatch(a -> a.contains("regras locais"));
    }

    @Test
    void demandaForaDaTriagemResponde409() {
        when(demandas.detalhar(ator, 4L)).thenReturn(demanda(StatusDemanda.DISTRIBUIDA));
        assertThatThrownBy(() -> service(new ModeloRoteiro()).sugerir(ator, 4L)).isInstanceOf(ConflitoException.class);
    }

    @Test
    void justificativaTerminaEmFraseCompleta() {
        String longa = "Primeira frase com fatos relevantes da demanda. Segunda frase interrompida pelo limi";
        assertThat(AgenteTriagemService.frasesCompletas(longa, 200))
                .isEqualTo("Primeira frase com fatos relevantes da demanda.");
        assertThat(AgenteTriagemService.frasesCompletas("Frase completa.", 200)).isEqualTo("Frase completa.");
        assertThat(AgenteTriagemService.frasesCompletas("Um um. Dois dois. Três três. Quatro quatro.", 200))
                .isEqualTo("Um um. Dois dois. Três três.");
        assertThat(AgenteTriagemService.frasesCompletas("x".repeat(300), 100)).hasSize(101).endsWith("…");
    }

    @Test
    void regrasLocais() {
        assertThat(RegrasTriagem.categoria("Queimada em pastagem", "Fogo próximo à mata"))
                .isEqualTo(CategoriaFiscalizacao.QUEIMADA_INCENDIO);
        assertThat(RegrasTriagem.categoria("Pesca irregular em período de defeso", ""))
                .isEqualTo(CategoriaFiscalizacao.PESCA_IRREGULAR);
        assertThat(RegrasTriagem.categoria("Reclamação", "Sem detalhes")).isNull();
        assertThat(RegrasTriagem.prioridade(CategoriaFiscalizacao.QUEIMADA_INCENDIO, "Queimada", "", 0))
                .isEqualTo(Prioridade.ALTA);
        assertThat(RegrasTriagem.prioridade(CategoriaFiscalizacao.QUEIMADA_INCENDIO, "Queimada em andamento", "", 0))
                .isEqualTo(Prioridade.URGENTE);
        assertThat(RegrasTriagem.prioridade(CategoriaFiscalizacao.RESIDUOS_POLUICAO, "Lixo", "", 80))
                .isEqualTo(Prioridade.ALTA);
        assertThat(RegrasTriagem.nucleo("Cáceres")[0]).isEqualTo("NR-CAC");
        assertThat(RegrasTriagem.nucleo("Outra")).isNull();
    }

    /** Stream SSE (Issue #135): eventos das ferramentas e fallback, com a mesma sugestão do endpoint JSON. */
    static class Eventos implements br.com.nexusbuilderlab.semanexus.agentes.ObservadorAgente {
        final List<br.com.nexusbuilderlab.semanexus.agentes.EventoAgente> lista = new java.util.ArrayList<>();

        @Override
        public void emitir(br.com.nexusbuilderlab.semanexus.agentes.EventoAgente evento) {
            lista.add(evento);
        }
    }

    @Test
    void streamEmiteEventosDasFerramentas() throws Exception {
        when(demandas.detalhar(ator, 4L)).thenReturn(demanda(StatusDemanda.EM_TRIAGEM));
        ModeloRoteiro modelo = new ModeloRoteiro();
        modelo.respostas.add(ferramenta("obter_demanda", "{}"));
        modelo.respostas.add(new ClienteModelo.RespostaModelo("stop", List.of(),
                "{\"decisao\":\"CLASSIFICAR\",\"categoria\":\"RESIDUOS_POLUICAO\",\"prioridade\":\"MEDIA\","
                        + "\"justificativa\":\"Descarte relatado.\",\"duplicatas\":[]}", null, 50, 30, 0));
        AgenteTriagemService service = service(modelo);
        Eventos eventos = new Eventos();

        SugestaoTriagem s = service.sugerir(ator, service.verificar(ator, 4L), eventos);

        assertThat(s.modo()).isEqualTo("LOCAL");
        assertThat(eventos.lista).extracting(br.com.nexusbuilderlab.semanexus.agentes.EventoAgente::tipo)
                .containsExactly("ferramenta_iniciada", "ferramenta_concluida");
    }

    @Test
    void streamEmiteFallbackQuandoAIaFalhaOuEstaIndisponivel() {
        when(demandas.detalhar(ator, 4L)).thenReturn(demanda(StatusDemanda.PENDENTE));
        Eventos falha = new Eventos();
        SugestaoTriagem s = service(new ModeloRoteiro()).sugerir(ator, demanda(StatusDemanda.PENDENTE), falha);
        assertThat(s.modo()).isEqualTo("DEMONSTRATIVO");
        assertThat(falha.lista).extracting(br.com.nexusbuilderlab.semanexus.agentes.EventoAgente::tipo)
                .containsExactly("fallback_demonstrativo");
        // Motivo público fixo: nunca a mensagem da exceção ("fim do roteiro", tipo do erro...).
        assertThat(falha.lista.get(0).dados().get("motivo").toString()).doesNotContain("roteiro")
                .doesNotContain("Exception");

        ModeloRoteiro semIa = new ModeloRoteiro();
        semIa.disponivel = false;
        Eventos indisponivel = new Eventos();
        service(semIa).sugerir(ator, demanda(StatusDemanda.PENDENTE), indisponivel);
        assertThat(indisponivel.lista).extracting(br.com.nexusbuilderlab.semanexus.agentes.EventoAgente::tipo)
                .containsExactly("fallback_demonstrativo");
    }
}
