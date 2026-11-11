package br.com.nexusbuilderlab.semanexus.docia.storage;

import java.io.IOException;

/** Armazenamento de arquivos fora do banco. A chave devolvida por {@link #store} é opaca (ex.: AAAA/MM/uuid). */
public interface StorageService {

    /** Grava o conteúdo em um caminho gerado pelo sistema (o nome original nunca entra no caminho) e devolve a chave. */
    String store(byte[] conteudo) throws IOException;

    /** Lê o conteúdo da chave; falha se a chave tentar sair do diretório de armazenamento ou não existir. */
    byte[] load(String chave) throws IOException;

    /** Remove o arquivo da chave (silencioso se não existir). */
    void delete(String chave) throws IOException;
}
