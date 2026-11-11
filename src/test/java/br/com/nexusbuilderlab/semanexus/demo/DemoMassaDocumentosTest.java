package br.com.nexusbuilderlab.semanexus.demo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nexusbuilderlab.semanexus.demo.DemoMassaDocumentos.DocSeed;
import br.com.nexusbuilderlab.semanexus.docia.documento.TextExtractor;
import br.com.nexusbuilderlab.semanexus.docia.documento.TipoArquivo;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoMassaDocumentosTest {

    @Test
    void catalogoTemDozeDocumentosComDuasVersoesDoParecerEVinculoACenario() {
        List<DocSeed> docs = DemoMassaDocumentos.catalogo();
        assertThat(docs).hasSize(12);
        assertThat(docs.stream().filter(d -> d.titulo().startsWith("Parecer Técnico nº 014/2026")).count())
                .isEqualTo(2);
        assertThat(docs).allSatisfy(d -> {
            assertThat(DemoMassaGerador.CENARIOS.stream().anyMatch(c -> c.chave().equals(d.demandaChave()))).isTrue();
            assertThat(DemoMassaGerador.MUNICIPIOS_IDS).contains(d.municipioId());
        });
    }

    @Test
    void arquivosSaoValidosEIdentificadosComoFicticios() {
        TextExtractor extrator = new TextExtractor();
        for (DocSeed d : DemoMassaDocumentos.catalogo()) {
            TipoArquivo tipo = TipoArquivo.porNome(d.nomeArquivo()).orElseThrow();
            byte[] bytes = d.conteudo();
            assertThat(tipo.conteudoValido(bytes)).as(d.nomeArquivo()).isTrue();
            assertThat(extrator.extrair(tipo, bytes)).as(d.nomeArquivo()).contains("FICTÍCIOS");
        }
    }
}
