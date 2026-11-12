package br.com.nexusbuilderlab.semanexus.docia.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoDetalhe;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoFiltro;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaService;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaRequest;
import org.springframework.data.domain.PageRequest;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoUploadRequest;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/** Issue #15 contra Oracle real: V4 aplicada, upload persiste metadados e texto (CLOB) e o download devolve o arquivo. */
@Tag("oracle")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class DocumentoOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    static final java.nio.file.Path VOLUME = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "sema-docs-" + UUID.randomUUID());

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry r) {
        r.add("semanexus.storage.dir", VOLUME::toString);
    }

    @Autowired
    DocumentoService service;
    @Autowired
    UsuarioRepository usuarios;

    @Test
    void uploadPersisteMetadadosTextoEArquivo() {
        Usuario u = usuarios.save(new Usuario("Analista Teste", "an." + UUID.randomUUID() + "@exemplo.test", "hash", Perfil.ANALISTA));
        UsuarioAutenticado ator = UsuarioAutenticado.de(u);
        byte[] txt = "Relatório fictício de vistoria".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        DocumentoDetalhe d = service.enviar(ator, new DocumentoUploadRequest(
                new MockMultipartFile("arquivo", "relatorio.txt", "text/plain", txt), "Relatório de teste", null,
                "Relatório", "Laudo", LocalDate.now(), 5103403, List.of("teste"), null));
        assertThat(d.id()).isNotNull();
        assertThat(d.textoExtraido()).isEqualTo("Relatório fictício de vistoria");
        assertThat(d.municipio().nome()).isEqualTo("Cuiabá");
        assertThat(service.baixar(d.id()).conteudo()).isEqualTo(txt);
    }

    @Autowired
    DemandaService demandas;

    private DocumentoDetalhe enviar(UsuarioAutenticado ator, String titulo, String texto, String categoria) {
        return service.enviar(ator, new DocumentoUploadRequest(
                new MockMultipartFile("arquivo", "doc.txt", "text/plain", texto.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                titulo, null, categoria, "Laudo", LocalDate.now(), null, List.of(), null));
    }

    private static DocumentoFiltro q(String termo) {
        return new DocumentoFiltro(termo, null, null, null, null, null, null);
    }

    @Test
    void pesquisaIgnoraAcentosECaixaEEncontraTextoExtraido() {
        Usuario u = usuarios.save(new Usuario("Analista Busca", "bu." + UUID.randomUUID() + "@exemplo.test", "hash", Perfil.ANALISTA));
        UsuarioAutenticado ator = UsuarioAutenticado.de(u);
        String marca = "zq" + UUID.randomUUID().toString().substring(0, 8);
        DocumentoDetalhe porTitulo = enviar(ator, "Relatório de Fiscalização " + marca, "conteudo comum", "Relatório");
        DocumentoDetalhe porTexto = enviar(ator, "Outro documento " + marca, "O parecer técnico conclui pela AUTUAÇÃO", "Parecer");
        var pagina = PageRequest.of(0, 20);

        assertThat(service.listar(q("fiscalizacao " + marca), pagina).content()).extracting("id").containsExactly(porTitulo.id());
        assertThat(service.listar(q("FISCALIZAÇÃO"), pagina).content()).extracting("id").contains(porTitulo.id());
        assertThat(service.listar(q("autuacao"), pagina).content()).extracting("id").contains(porTexto.id());
        assertThat(service.listar(q("parecer tecnico"), pagina).content()).extracting("id").contains(porTexto.id());
        assertThat(service.listar(new DocumentoFiltro(marca, "parecer", null, null, null, null, null), pagina).content())
                .extracting("id").containsExactly(porTexto.id());
        assertThat(service.listar(q("100%_inexistente"), pagina).totalElements()).isZero();
        assertThat(service.detalhar(porTexto.id()).historico()).extracting("acao").contains("UPLOAD");
    }

    @Test
    void vinculoRegistraHistoricoDuplicadoRetorna409EDesvinculaRemove() {
        Usuario u = usuarios.save(new Usuario("Analista Vinculo", "vi." + UUID.randomUUID() + "@exemplo.test", "hash", Perfil.ANALISTA));
        UsuarioAutenticado ator = UsuarioAutenticado.de(u);
        Long demandaId = demandas.cadastrar(ator, new DemandaRequest("Demanda para vínculo", "Descrição fictícia",
                Origem.DENUNCIA, null, 5103403, null, null, null, LocalDate.now(), null, null, null)).id();
        Long docId = enviar(ator, "Doc a vincular", "texto", "Relatório").id();

        service.vincular(ator, demandaId, docId);
        assertThatThrownBy(() -> service.vincular(ator, demandaId, docId)).isInstanceOf(ConflitoException.class);
        assertThat(service.listarDaDemanda(ator, demandaId)).extracting("id").containsExactly(docId);
        assertThat(service.detalhar(docId).demandas()).hasSize(1);
        assertThat(demandas.historico(ator, demandaId)).extracting("texto")
                .anyMatch(t -> String.valueOf(t).contains("Documento vinculado"));

        service.desvincular(ator, demandaId, docId);
        assertThat(service.listarDaDemanda(ator, demandaId)).isEmpty();
        assertThat(demandas.historico(ator, demandaId)).extracting("texto")
                .anyMatch(t -> String.valueOf(t).contains("Documento desvinculado"));
        assertThatThrownBy(() -> service.desvincular(ator, demandaId, docId))
                .isInstanceOf(br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException.class);
    }
}
