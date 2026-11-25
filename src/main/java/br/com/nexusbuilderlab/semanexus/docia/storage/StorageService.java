package br.com.nexusbuilderlab.semanexus.docia.storage;

import br.com.nexusbuilderlab.semanexus.docia.documento.ArquivoNaoSuportadoException;
import java.io.IOException;

/** Armazenamento de arquivos fora do banco. A chave devolvida por {@link #store} é opaca (ex.: AAAA/MM/uuid). */
public interface StorageService {

    /** Grava o conteúdo em um caminho gerado pelo sistema (o nome original nunca entra no caminho) e devolve a chave. */
    String store(byte[] conteudo) throws IOException;

    /** Lê o conteúdo da chave; falha se a chave tentar sair do diretório de armazenamento ou não existir. */
    byte[] load(String chave) throws IOException;

    /** Remove o arquivo da chave (silencioso se não existir). */
    void delete(String chave) throws IOException;

    /**
     * Valida a imagem pelos <em>magic bytes</em> (JPEG, PNG ou WebP; Issue #145) antes de qualquer gravação. Qualquer
     * outro conteúdo — inclusive um executável ou PDF com extensão {@code .jpg} — é recusado com HTTP 415.
     */
    default TipoImagem validarImagem(byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new ArquivoNaoSuportadoException("Arquivo vazio.");
        }
        return TipoImagem.detectar(conteudo).orElseThrow(() -> new ArquivoNaoSuportadoException(
                "Conteúdo não reconhecido como imagem. Envie JPEG, PNG ou WebP."));
    }
}
