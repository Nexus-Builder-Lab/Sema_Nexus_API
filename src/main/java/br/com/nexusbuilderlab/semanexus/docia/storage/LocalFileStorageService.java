package br.com.nexusbuilderlab.semanexus.docia.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Grava em {@code ${STORAGE_DIR}/AAAA/MM/<uuid>}. Toda chave é resolvida e confinada ao diretório raiz. */
@Service
public class LocalFileStorageService implements StorageService {

    private final Path raiz;
    private final Clock clock;

    @Autowired
    public LocalFileStorageService(@Value("${semanexus.storage.dir:/data/uploads}") String diretorio) {
        this(Path.of(diretorio), Clock.systemDefaultZone());
    }

    LocalFileStorageService(Path raiz, Clock clock) {
        this.raiz = raiz.toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Override
    public String store(byte[] conteudo) throws IOException {
        LocalDate hoje = LocalDate.now(clock);
        String chave = "%04d/%02d/%s".formatted(hoje.getYear(), hoje.getMonthValue(), UUID.randomUUID());
        Path destino = resolver(chave);
        Files.createDirectories(destino.getParent());
        Files.write(destino, conteudo);
        return chave;
    }

    @Override
    public byte[] load(String chave) throws IOException {
        return Files.readAllBytes(resolver(chave));
    }

    @Override
    public void delete(String chave) throws IOException {
        Files.deleteIfExists(resolver(chave));
    }

    private Path resolver(String chave) {
        Path caminho = raiz.resolve(chave).normalize();
        if (!caminho.startsWith(raiz) || caminho.equals(raiz)) {
            throw new IllegalArgumentException("Chave de armazenamento inválida.");
        }
        return caminho;
    }
}
