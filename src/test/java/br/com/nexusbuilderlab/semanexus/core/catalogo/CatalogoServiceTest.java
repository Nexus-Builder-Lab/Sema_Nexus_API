package br.com.nexusbuilderlab.semanexus.core.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.nexusbuilderlab.semanexus.core.catalogo.dto.ItemCatalogo;
import br.com.nexusbuilderlab.semanexus.core.catalogo.dto.MunicipioDto;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock
    MunicipioRepository municipios;
    @InjectMocks
    CatalogoService service;

    private static Municipio municipio(int id, String nome, Bioma bioma) {
        Municipio m = BeanUtils.instantiateClass(Municipio.class); // construtor protegido (entidade @Immutable)
        ReflectionTestUtils.setField(m, "id", id);
        ReflectionTestUtils.setField(m, "nome", nome);
        ReflectionTestUtils.setField(m, "regiao", "Região de teste");
        ReflectionTestUtils.setField(m, "bioma", bioma);
        ReflectionTestUtils.setField(m, "latitude", new BigDecimal("-15.601000"));
        ReflectionTestUtils.setField(m, "longitude", new BigDecimal("-56.097000"));
        return m;
    }

    private static List<String> codigos(List<ItemCatalogo> itens) {
        return itens.stream().map(ItemCatalogo::codigo).toList();
    }

    @Test
    void statusTemOs7StatusNaOrdemDoFluxoSemAtrasada() {
        List<ItemCatalogo> status = service.status();

        assertThat(codigos(status)).containsExactly("PENDENTE", "EM_TRIAGEM", "AGUARDANDO_DISTRIBUICAO", "DISTRIBUIDA",
                "EM_ANDAMENTO", "AGUARDANDO_CONCLUSAO", "FINALIZADA");
        assertThat(status).extracting(ItemCatalogo::rotulo).containsExactly("Pendente", "Em triagem",
                "Aguardando distribuição", "Distribuída", "Em andamento", "Aguardando conclusão", "Finalizada");
        assertThat(status).extracting(ItemCatalogo::ordem).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(codigos(status)).doesNotContain("ATRASADA");
    }

    @Test
    void origensSaoAsCincoDaEspecificacao() {
        assertThat(service.origens()).extracting(ItemCatalogo::rotulo).containsExactly("Denúncia", "Monitoramento",
                "Solicitação institucional", "Ação planejada", "Análise ambiental");
    }

    @Test
    void categoriasSaoAsOitoDeFiscalizacao() {
        assertThat(service.categorias()).extracting(ItemCatalogo::rotulo).containsExactly("Desmatamento ilegal",
                "Queimada/incêndio", "Recursos hídricos", "Fauna silvestre", "Pesca irregular", "Mineração irregular",
                "Resíduos e poluição", "Área de preservação permanente");
    }

    @Test
    void prioridadesDaMenorParaAMaior() {
        assertThat(service.prioridades()).extracting(ItemCatalogo::codigo)
                .containsExactly("BAIXA", "MEDIA", "ALTA", "URGENTE");
        assertThat(service.prioridades()).extracting(ItemCatalogo::rotulo)
                .containsExactly("Baixa", "Média", "Alta", "Urgente");
    }

    @Test
    void biomasSaoOsTresDeMatoGrosso() {
        assertThat(service.biomas()).extracting(ItemCatalogo::codigo).containsExactly("AMAZONIA", "CERRADO", "PANTANAL");
        assertThat(service.biomas()).extracting(ItemCatalogo::rotulo).containsExactly("Amazônia", "Cerrado", "Pantanal");
    }

    @Test
    void codigosDosCatalogosCombinamComOsValoresDoBanco() {
        // Os nomes dos enums são gravados nos CHECKs do banco: não podem mudar sem migration.
        assertThat(Bioma.valueOf("AMAZONIA").rotulo()).isEqualTo("Amazônia");
        assertThat(codigos(service.origens())).doesNotHaveDuplicates();
        assertThat(codigos(service.categorias())).doesNotHaveDuplicates();
    }

    @Test
    void municipiosSemFiltroSaoOrdenadosAlfabeticamenteEmPortuguesIndependenteDoBanco() {
        // O banco devolve em ordem arbitrária (ou binária, com acentuadas no fim): o service ordena.
        when(municipios.findAll()).thenReturn(List.of(
                municipio(5108402, "Várzea Grande", Bioma.CERRADO),
                municipio(5100102, "Água Boa", Bioma.CERRADO),
                municipio(5103403, "Cuiabá", Bioma.CERRADO),
                municipio(5100201, "Acorizal", Bioma.CERRADO),
                municipio(5101407, "Campos de Júlio", Bioma.AMAZONIA),
                municipio(5101308, "Campo Verde", Bioma.CERRADO)));

        List<MunicipioDto> lista = service.municipios(null);

        assertThat(lista).extracting(MunicipioDto::nome)
                .containsExactly("Acorizal", "Água Boa", "Campo Verde", "Campos de Júlio", "Cuiabá", "Várzea Grande");
        assertThat(lista.get(4).id()).isEqualTo(5103403);
        assertThat(lista.get(4).bioma()).isEqualTo(Bioma.CERRADO);
        assertThat(lista.get(4).latitude()).isEqualByComparingTo("-15.601");
    }

    @Test
    void municipioEmListaDeUmElementoMantemTodosOsCampos() {
        when(municipios.findAll()).thenReturn(List.of(municipio(5103403, "Cuiabá", Bioma.CERRADO)));

        List<MunicipioDto> lista = service.municipios(null);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).regiao()).isEqualTo("Região de teste");
        assertThat(lista.get(0).longitude()).isEqualByComparingTo("-56.097");
    }

    @Test
    void municipiosComBiomaUsamOFiltroDoRepositorioETambemSaoOrdenados() {
        when(municipios.findByBioma(Bioma.PANTANAL)).thenReturn(List.of(
                municipio(5106505, "Poconé", Bioma.PANTANAL),
                municipio(5100250, "Barão de Melgaço", Bioma.PANTANAL),
                municipio(5105507, "Nossa Senhora do Livramento", Bioma.PANTANAL)));

        assertThat(service.municipios(Bioma.PANTANAL)).extracting(MunicipioDto::nome)
                .containsExactly("Barão de Melgaço", "Nossa Senhora do Livramento", "Poconé");
        verify(municipios).findByBioma(Bioma.PANTANAL);
        verify(municipios, never()).findAll();
    }

    @Test
    void chaveDeOrdenacaoIgnoraAcentosEMaiusculas() {
        assertThat(CatalogoService.chaveDeOrdenacao("Água Boa")).isEqualTo("agua boa");
        assertThat(CatalogoService.chaveDeOrdenacao("São José do Rio Claro")).isEqualTo("sao jose do rio claro");
    }

    @Test
    void catalogosDeEnumNaoConsultamOBanco() {
        service.status();
        service.origens();
        service.categorias();
        service.prioridades();
        service.biomas();

        verifyNoInteractions(municipios);
    }
}
