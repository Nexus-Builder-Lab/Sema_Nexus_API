package br.com.nexusbuilderlab.semanexus.docia.documento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.DocumentoDetalhe;
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
@Testcontainers
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
}
