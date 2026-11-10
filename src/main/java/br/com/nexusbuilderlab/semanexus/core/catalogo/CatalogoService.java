package br.com.nexusbuilderlab.semanexus.core.catalogo;

import br.com.nexusbuilderlab.semanexus.core.catalogo.dto.ItemCatalogo;
import br.com.nexusbuilderlab.semanexus.core.catalogo.dto.MunicipioDto;
import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import br.com.nexusbuilderlab.semanexus.core.municipio.Municipio;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogos de domínio: enums fixos da API e municípios de MT lidos do Oracle. */
@Service
public class CatalogoService {

    private final MunicipioRepository municipios;

    public CatalogoService(MunicipioRepository municipios) {
        this.municipios = municipios;
    }

    /**
     * Municípios de MT em ordem alfabética palavra a palavra, sem diferenciar acentos nem maiúsculas
     * ("Água Boa" entre "Acorizal" e "Alta Floresta"; "Campo Verde" antes de "Campos de Júlio"),
     * opcionalmente filtrados por bioma predominante. A ordenação é feita aqui, e não no {@code ORDER BY},
     * para não depender do {@code NLS_SORT} do banco (são só 142 linhas).
     */
    @Transactional(readOnly = true)
    public List<MunicipioDto> municipios(Bioma bioma) {
        var lista = bioma == null ? municipios.findAll() : municipios.findByBioma(bioma);
        return lista.stream()
                .sorted(Comparator.comparing((Municipio m) -> chaveDeOrdenacao(m.getNome()))
                        .thenComparing(Municipio::getId))
                .map(MunicipioDto::de)
                .toList();
    }

    /** Nome sem acentos e em minúsculas: letras acentuadas ordenam junto às suas bases. */
    static String chaveDeOrdenacao(String nome) {
        return Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    public List<ItemCatalogo> biomas() {
        return itens(Bioma.values());
    }

    public List<ItemCatalogo> categorias() {
        return itens(CategoriaFiscalizacao.values());
    }

    public List<ItemCatalogo> origens() {
        return itens(Origem.values());
    }

    public List<ItemCatalogo> prioridades() {
        return itens(Prioridade.values());
    }

    public List<ItemCatalogo> status() {
        return itens(StatusDemanda.values());
    }

    private static List<ItemCatalogo> itens(Catalogavel[] valores) {
        return IntStream.range(0, valores.length)
                .mapToObj(i -> ItemCatalogo.de(valores[i], i + 1))
                .toList();
    }
}
