package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FiscalizacaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FotoFiscalizacao;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Issue #145 contra Oracle real (V12): foto com EXIF/GPS gravada no volume e metadados em FISCALIZACAO_FOTO,
 * checklist em JSON com {@code IS JSON} e restrições da tabela. Fora do build padrão: {@code mvn -B -ntp verify -Poracle}.
 */
@Tag("oracle")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "semanexus.jwt.secret=segredo-ficticio-somente-para-teste-0123456789")
@ActiveProfiles("demo")
class FiscalizacaoFotoOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    @org.springframework.test.context.DynamicPropertySource
    static void armazenamento(org.springframework.test.context.DynamicPropertyRegistry registry) throws IOException {
        var dir = java.nio.file.Files.createTempDirectory("semanexus-fotos");
        registry.add("semanexus.storage.dir", dir::toString);
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    FotoFiscalizacaoService fotos;
    @Autowired
    FiscalizacaoService fiscalizacao;
    @Autowired
    DemandaService demandas;
    @Autowired
    UsuarioRepository usuarios;

    private static byte[] recurso(String nome) throws IOException {
        try (InputStream in = FiscalizacaoFotoOracleTest.class.getResourceAsStream("/fotos/" + nome)) {
            return in.readAllBytes();
        }
    }

    private Map<String, Object> demandaEmAndamento() {
        return jdbc.queryForMap("SELECT ID, FISCAL_ID, CATEGORIA FROM DEMANDA WHERE STATUS = 'EM_ANDAMENTO' "
                + "AND FISCAL_ID IS NOT NULL AND EQUIPE_ID IS NOT NULL AND EXCLUIDO_EM IS NULL "
                + "AND ID NOT IN (SELECT DEMANDA_ID FROM FISCALIZACAO_FOTO) ORDER BY ID FETCH FIRST 1 ROWS ONLY");
    }

    @Test
    void fotoComGpsEChecklistGravadosNoOracle() throws IOException {
        Map<String, Object> linha = demandaEmAndamento();
        long id = ((Number) linha.get("ID")).longValue();
        Usuario fiscal = usuarios.findById(((Number) linha.get("FISCAL_ID")).longValue()).orElseThrow();
        UsuarioAutenticado ator = UsuarioAutenticado.de(fiscal);

        FotoFiscalizacao foto = fotos.enviar(ator, id,
                new MockMultipartFile("arquivo", "vistoria.jpg", "image/jpeg", recurso("gps-cuiaba.jpg")));
        assertThat(foto.id()).isNotNull();
        assertThat(foto.exifLatitude()).isNotNull();
        assertThat(foto.distanciaKm()).isNotNull();
        Map<String, Object> salvo = jdbc.queryForMap("SELECT SHA256, MIME, GPS_FORA_MT, CHAVE_ARQUIVO, "
                + "EXIF_DATA_HORA FROM FISCALIZACAO_FOTO WHERE ID = ?", foto.id());
        assertThat(salvo.get("SHA256")).isEqualTo(foto.sha256());
        assertThat(salvo.get("MIME")).isEqualTo("image/jpeg");
        assertThat(salvo.get("GPS_FORA_MT")).isEqualTo(Boolean.FALSE);
        assertThat(salvo.get("EXIF_DATA_HORA")).isNotNull();
        assertThat(fotos.listar(ator, id)).extracting(FotoFiscalizacao::id).containsExactly(foto.id());
        assertThat(fotos.baixar(ator, id, foto.id()).conteudo()).isEqualTo(recurso("gps-cuiaba.jpg"));

        // Checklist validado contra a categoria da demanda e gravado como JSON (IS JSON).
        var categoria = br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao.valueOf(
                (String) linha.get("CATEGORIA"));
        Map<String, Object> respostas = new HashMap<>();
        for (var item : ChecklistFiscalizacao.modelo(categoria).itens()) {
            if (item.obrigatorio()) {
                respostas.put(item.id(), switch (item.tipo()) {
                    case BOOLEANO -> true;
                    case NUMERO -> 2.5;
                    case TEXTO -> "Texto fictício";
                });
            }
        }
        DemandaDetalhe d = fiscalizacao.registrar(ator, id, new FiscalizacaoRequest(
                LocalDate.now(ZoneId.of("America/Cuiaba")), "Vistoria fictícia", "Sem irregularidades",
                "Fotos demonstrativas", null, respostas));
        assertThat(d.fiscalizacao().checklist().categoria()).isEqualTo(categoria);
        assertThat(d.fiscalizacao().checklist().itens()).containsOnlyKeys(respostas.keySet());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM FISCALIZACAO WHERE DEMANDA_ID = ? AND CHECKLIST IS JSON",
                Long.class, id)).isEqualTo(1);

        // A demanda saiu de EM_ANDAMENTO: novo envio é recusado; o autor ainda exclui antes da conclusão.
        assertThatThrownBy(() -> fotos.enviar(ator, id,
                new MockMultipartFile("arquivo", "b.jpg", "image/jpeg", recurso("sem-gps.jpg"))))
                .isInstanceOf(br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException.class);
        fotos.excluir(ator, id, foto.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM FISCALIZACAO_FOTO WHERE DEMANDA_ID = ?", Long.class, id))
                .isZero();
    }

    @Test
    void restricoesDaV12() {
        long id = ((Number) demandaEmAndamento().get("ID")).longValue();
        long autor = jdbc.queryForObject("SELECT MIN(ID) FROM USUARIO", Long.class);
        String insert = "INSERT INTO FISCALIZACAO_FOTO (DEMANDA_ID, CHAVE_ARQUIVO, SHA256, MIME, TAMANHO, "
                + "NOME_ORIGINAL, EXIF_LATITUDE, EXIF_LONGITUDE, AUTOR_ID) VALUES (?, ?, ?, ?, 10, 'x.jpg', ?, ?, ?)";
        String sha = "a".repeat(64);
        assertThatThrownBy(() -> jdbc.update(insert, id, "k/1", sha, "image/gif", null, null, autor))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, id, "k/2", sha, "image/jpeg", -15.6, null, autor))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE FISCALIZACAO SET CHECKLIST = 'não é json' "
                + "WHERE ID = (SELECT MIN(ID) FROM FISCALIZACAO)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        List<String> colunas = jdbc.queryForList("SELECT COLUMN_NAME FROM USER_TAB_COLUMNS "
                + "WHERE TABLE_NAME = 'FISCALIZACAO_FOTO'", String.class);
        assertThat(colunas).contains("SHA256", "CHAVE_ARQUIVO", "EXIF_DATA_HORA", "DISTANCIA_KM", "GPS_FORA_MT")
                .doesNotContain("CONTEUDO", "ARQUIVO");
    }
}
