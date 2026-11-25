package br.com.nexusbuilderlab.semanexus.docia.documento;

import java.util.List;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.docia.assistente.BuscaDocumentos;
import br.com.nexusbuilderlab.semanexus.docia.documento.dto.PesquisaDocumentos;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pesquisa ranqueada da biblioteca (Issue #141): mesma busca do Assistente DocIA ({@link BuscaDocumentos}), híbrida
 * quando há embeddings e lexical caso contrário. Só consulta; a listagem paginada com filtros continua em
 * {@code GET /api/documentos}.
 */
@Service
public class PesquisaDocumentosService {

    public static final int LIMITE_PADRAO = 10;
    public static final int LIMITE_MAXIMO = 20;

    private final BuscaDocumentos busca;
    private final TransactionTemplate transacao;

    public PesquisaDocumentosService(BuscaDocumentos busca, ObjectProvider<PlatformTransactionManager> transacoes) {
        this.busca = busca;
        PlatformTransactionManager tm = transacoes.getIfAvailable();
        this.transacao = tm == null ? null : new TransactionTemplate(tm);
        if (this.transacao != null) {
            this.transacao.setReadOnly(true);
        }
    }

    /** @param modo {@code AUTO} (padrão: híbrida quando disponível) ou {@code LEXICAL} (só textual) */
    public PesquisaDocumentos pesquisar(String q, Integer limite, String modo) {
        String consulta = q == null ? "" : q.strip();
        if (consulta.length() < 2 || consulta.length() > 200) {
            throw new DadosInvalidosException("q", "Informe de 2 a 200 caracteres.");
        }
        int n = limite == null ? LIMITE_PADRAO : limite;
        if (n < 1 || n > LIMITE_MAXIMO) {
            throw new DadosInvalidosException("limite", "Use um valor de 1 a " + LIMITE_MAXIMO + ".");
        }
        String m = modo == null || modo.isBlank() ? "AUTO" : modo.strip().toUpperCase(java.util.Locale.ROOT);
        if (!m.equals("AUTO") && !m.equals("LEXICAL")) {
            throw new DadosInvalidosException("modo", "Use AUTO ou LEXICAL.");
        }
        // Embedding da consulta (rede) fora da transação: a conexão do banco só é usada depois.
        BuscaDocumentos.ConsultaPreparada preparada = busca.preparar(consulta, m.equals("AUTO"));
        return transacao == null ? consultar(preparada, n) : transacao.execute(st -> consultar(preparada, n));
    }

    private PesquisaDocumentos consultar(BuscaDocumentos.ConsultaPreparada preparada, int n) {
        BuscaDocumentos.Resultado r = busca.pesquisar(preparada, n, null);
        List<PesquisaDocumentos.Item> itens = r.achados().stream()
                .map(a -> new PesquisaDocumentos.Item(DocumentoService.resumo(a.documento()), a.trecho(),
                        a.aderencia(), a.similaridadeSemantica()))
                .toList();
        return new PesquisaDocumentos(preparada.texto(), r.recuperacao(), itens);
    }
}
