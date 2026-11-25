package br.com.nexusbuilderlab.semanexus.docia.documento;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Coluna derivada {@code DOCUMENTO.TEXTO_BUSCA} (Issue #164): o texto extraído normalizado pela aplicação
 * ({@link DocumentoSpecification#textoBusca(String)}), para a busca lexical comparar o CLOB com {@code LIKE} sem
 * TRANSLATE/LOWER (que estouram com ORA-64203 em textos longos e acentuados).
 * <ul>
 *   <li>upload: {@link DocumentoService} grava a coluna na mesma transação;</li>
 *   <li>subida da API: preenche documentos antigos ou gravados por fora ({@code TEXTO_BUSCA} nulo com texto
 *   extraído) — idempotente: documento já preenchido não é refeito. Falha não impede a subida.</li>
 * </ul>
 * Só grava a coluna derivada; nunca altera o conteúdo do documento.
 */
@Component
@Profile("!openapi")
@Order(40)
public class IndiceBuscaDocumentos implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(IndiceBuscaDocumentos.class);

    static final String SQL_ATUALIZAR = "UPDATE DOCUMENTO SET TEXTO_BUSCA = ? WHERE ID = ?";
    static final String SQL_PENDENTES =
            "SELECT ID FROM DOCUMENTO WHERE TEXTO_BUSCA IS NULL AND TEXTO_EXTRAIDO IS NOT NULL ORDER BY ID";
    static final String SQL_TEXTO = "SELECT TEXTO_EXTRAIDO FROM DOCUMENTO WHERE ID = ?";

    private final JdbcTemplate jdbc;

    public IndiceBuscaDocumentos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Grava o texto normalizado de um documento (nada a fazer se não há texto extraído). */
    static void atualizar(JdbcTemplate jdbc, Long id, String textoExtraido) {
        if (id != null && textoExtraido != null) {
            jdbc.update(SQL_ATUALIZAR, DocumentoSpecification.textoBusca(textoExtraido), id);
        }
    }

    /** Preenche os documentos com texto extraído e sem {@code TEXTO_BUSCA}; devolve quantos foram preenchidos. */
    public int preencherPendentes() {
        List<Long> ids = jdbc.queryForList(SQL_PENDENTES, Long.class);
        int n = 0;
        for (Long id : ids) {
            String texto = jdbc.queryForObject(SQL_TEXTO, String.class, id);
            if (texto != null) {
                atualizar(jdbc, id, texto);
                n++;
            }
        }
        return n;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = preencherPendentes();
            if (n > 0) {
                LOG.info("Busca lexical: texto normalizado preenchido em {} documento(s).", n);
            }
        } catch (RuntimeException e) {
            LOG.error("Falha ao preencher o texto normalizado da busca (a API segue; nova tentativa na próxima "
                    + "subida): {}", e.toString());
        }
    }
}
