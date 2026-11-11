package br.com.nexusbuilderlab.semanexus.docia.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageServiceTest {

    @TempDir
    Path raiz;

    private LocalFileStorageService service() {
        return new LocalFileStorageService(raiz, Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void gravaEmAnoMesUuidELeDeVolta() throws IOException {
        String chave = service().store("conteudo".getBytes());
        assertThat(chave).matches("2026/11/[0-9a-f-]{36}");
        assertThat(Files.exists(raiz.resolve(chave))).isTrue();
        assertThat(service().load(chave)).isEqualTo("conteudo".getBytes());
        service().delete(chave);
        assertThat(Files.exists(raiz.resolve(chave))).isFalse();
    }

    @Test
    void chaveComTravessiaDeDiretorioNaoEscapaDaRaiz() throws IOException {
        Path fora = raiz.getParent().resolve("fora-" + System.nanoTime() + ".txt");
        Files.writeString(fora, "segredo");
        try {
            String relativo = "../" + fora.getFileName();
            assertThatThrownBy(() -> service().load(relativo)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service().delete(relativo)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service().load("2026/11/../../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
            assertThat(Files.exists(fora)).isTrue();
        } finally {
            Files.deleteIfExists(fora);
        }
    }
}
