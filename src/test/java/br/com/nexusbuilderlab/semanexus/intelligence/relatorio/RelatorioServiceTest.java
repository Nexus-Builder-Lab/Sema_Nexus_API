package br.com.nexusbuilderlab.semanexus.intelligence.relatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.flux.demanda.Demanda;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.IndicadorRepository;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorFiltro;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RelatorioServiceTest {

    static final IndicadorFiltro SEM_FILTRO = new IndicadorFiltro(null, null, null, null, null, null, null);

    IndicadorRepository repo = mock(IndicadorRepository.class);
    RelatorioService service = new RelatorioService(repo);

    Demanda demanda(String titulo) {
        Municipio m = mock(Municipio.class);
        when(m.getNome()).thenReturn("Cuiabá");
        when(m.getBioma()).thenReturn(Bioma.CERRADO);
        Demanda d = new Demanda("DEM-2026/1", new Usuario("A", "a@exemplo.test", "hash", Perfil.ANALISTA));
        d.setTitulo(titulo);
        d.setOrigem(Origem.DENUNCIA);
        d.setMunicipio(m);
        d.setCategoria(CategoriaFiscalizacao.values()[0]);
        d.setStatus(StatusDemanda.EM_ANDAMENTO);
        d.setDataRecebimento(LocalDate.of(2026, 3, 5));
        return d;
    }

    @Test
    void csvTemBomAvisoCabecalhoEAcentos() {
        List<Demanda> lista = List.of(demanda("Queimada ilegal"));
        when(repo.demandasPagina(any(), eq(0), eq(Integer.MAX_VALUE))).thenReturn(lista);
        byte[] bytes = service.csv(SEM_FILTRO);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        String[] linhas = csv.split("\r\n");
        assertThat(csv).startsWith("﻿AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS\r\n");
        assertThat(linhas[1]).startsWith("Número;Título;Categoria;Município");
        assertThat(linhas[2]).contains("DEM-2026/1;Queimada ilegal;").contains(";Cuiabá;Cerrado;Em andamento;")
                .contains("05/03/2026");
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    void csvEscapaSeparadorAspasEFormulas() {
        assertThat(RelatorioService.celula("a;b")).isEqualTo("\"a;b\"");
        assertThat(RelatorioService.celula("diz \"oi\"")).isEqualTo("\"diz \"\"oi\"\"\"");
        assertThat(RelatorioService.celula("=1+1")).isEqualTo("'=1+1");
        assertThat(RelatorioService.celula(null)).isEmpty();
    }

    @Test
    void filtrosSaoRepassadosAoCsv() {
        IndicadorFiltro f = new IndicadorFiltro(null, null, 5103403, Bioma.CERRADO, null, StatusDemanda.PENDENTE, null);
        when(repo.demandasPagina(any(), eq(0), eq(Integer.MAX_VALUE))).thenReturn(List.of());
        String csv = new String(service.csv(f), StandardCharsets.UTF_8);
        verify(repo).demandasPagina(f, 0, Integer.MAX_VALUE);
        assertThat(csv.split("\r\n")).hasSize(2);
    }

    @Test
    void paginaLimitaTamanhoECalculaTotais() {
        when(repo.contarDemandas(SEM_FILTRO)).thenReturn(250L);
        List<Demanda> lista = List.of(demanda("X"));
        when(repo.demandasPagina(SEM_FILTRO, 200, 100)).thenReturn(lista);
        var r = service.pagina(SEM_FILTRO, 2, 5000);
        assertThat(r.size()).isEqualTo(100);
        assertThat(r.totalPages()).isEqualTo(3);
        assertThat(r.content()).hasSize(1);
        assertThat(r.demonstrativo()).isTrue();
        assertThat(r.aviso()).contains("DADOS FICTÍCIOS");
    }

    @Test
    void periodoInvertidoRetorna400() {
        IndicadorFiltro f = new IndicadorFiltro(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1), null, null, null, null, null);
        assertThatThrownBy(() -> service.csv(f)).isInstanceOf(DadosInvalidosException.class);
    }
}
