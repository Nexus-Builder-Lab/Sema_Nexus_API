package br.com.nexusbuilderlab.semanexus.flux.demanda;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.lang.ByteArrayReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifReader;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * Lê data/hora e GPS do EXIF com {@code metadata-extractor} (Apache 2.0; Issue #145). Melhor esforço: EXIF ausente,
 * corrompido ou forjado resulta em campos nulos, nunca em erro. O EXIF é declarado pelo próprio arquivo —
 * informativo, sem valor de prova e nunca usado em autorização.
 */
final class LeitorExif {

    private static final DateTimeFormatter FORMATO_EXIF = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    /** Dados extraídos; qualquer campo pode ser nulo. Data/hora local da câmera (EXIF não traz fuso). */
    record Exif(LocalDateTime dataHora, Double latitude, Double longitude) {
        static final Exif VAZIO = new Exif(null, null, null);

        boolean temGps() {
            return latitude != null && longitude != null;
        }
    }

    private LeitorExif() {
    }

    static Exif ler(byte[] conteudo) {
        Metadata metadados;
        try {
            metadados = lerMetadados(conteudo);
            if (metadados == null) {
                return Exif.VAZIO;
            }
        } catch (Exception | StackOverflowError e) {
            return Exif.VAZIO;
        }
        LocalDateTime dataHora = data(metadados.getFirstDirectoryOfType(ExifSubIFDDirectory.class),
                ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
        if (dataHora == null) {
            dataHora = data(metadados.getFirstDirectoryOfType(ExifIFD0Directory.class), ExifIFD0Directory.TAG_DATETIME);
        }
        Double lat = null;
        Double lon = null;
        GpsDirectory gps = metadados.getFirstDirectoryOfType(GpsDirectory.class);
        if (gps != null) {
            GeoLocation g = gps.getGeoLocation();
            if (g != null && !g.isZero() && Double.isFinite(g.getLatitude()) && Double.isFinite(g.getLongitude())
                    && Math.abs(g.getLatitude()) <= 90 && Math.abs(g.getLongitude()) <= 180) {
                lat = g.getLatitude();
                lon = g.getLongitude();
            }
        }
        return new Exif(dataHora, lat, lon);
    }

    /**
     * Só JPEG e WebP têm leitura de EXIF. PNG é deliberadamente ignorado: o leitor de PNG da biblioteca descompacta
     * chunks zTXt/iTXt inteiros, sem limite (bomba de descompressão → OutOfMemoryError). No WebP os chunks RIFF são
     * percorridos diretamente sobre {@code c} antes de qualquer alocação, evitando {@code WebpMetadataReader}/
     * {@code RiffReader} que alocam {@code new byte[chunkSize]} pelo cabeçalho declarado. Nunca usar
     * {@code ImageMetadataReader}, que escolhe o leitor pelo conteúdo e alcançaria leitores inseguros.
     */
    private static Metadata lerMetadados(byte[] c) throws Exception {
        if (c.length >= 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8) {
            return JpegMetadataReader.readMetadata(new ByteArrayInputStream(c), List.of(new ExifReader()));
        }
        if (c.length >= 12 && c[0] == 'R' && c[1] == 'I' && c[2] == 'F' && c[3] == 'F'
                && c[8] == 'W' && c[9] == 'E' && c[10] == 'B' && c[11] == 'P') {
            return lerExifWebp(c);
        }
        return null;
    }

    private static Metadata lerExifWebp(byte[] c) {
        int pos = 12;
        while (pos <= c.length - 8) {
            long tamanho = Integer.toUnsignedLong(
                    (c[pos + 4] & 0xFF)
                            | ((c[pos + 5] & 0xFF) << 8)
                            | ((c[pos + 6] & 0xFF) << 16)
                            | ((c[pos + 7] & 0xFF) << 24));
            int dadosInicio = pos + 8;
            long restante = (long) c.length - dadosInicio;
            if (tamanho < 0 || tamanho > restante) {
                return null;
            }
            if (c[pos] == 'E' && c[pos + 1] == 'X' && c[pos + 2] == 'I' && c[pos + 3] == 'F' && tamanho > 0) {
                byte[] chunk = Arrays.copyOfRange(c, dadosInicio, dadosInicio + (int) tamanho);
                int base = ExifReader.startsWithJpegExifPreamble(chunk) ? 6 : 0;
                Metadata metadados = new Metadata();
                new ExifReader().extract(new ByteArrayReader(chunk, base), metadados);
                return metadados;
            }
            long proximo = (long) dadosInicio + tamanho + (tamanho & 1L);
            if (proximo > c.length || proximo <= pos) {
                return null;
            }
            pos = (int) proximo;
        }
        return null;
    }

    private static LocalDateTime data(Directory diretorio, int tag) {
        if (diretorio == null) {
            return null;
        }
        String texto = diretorio.getString(tag);
        if (texto == null || texto.length() < 19) {
            return null;
        }
        try {
            LocalDateTime d = LocalDateTime.parse(texto.substring(0, 19), FORMATO_EXIF);
            return d.getYear() < 1990 || d.getYear() > 2100 ? null : d;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
