package br.com.nexusbuilderlab.semanexus.flux.demanda;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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
            metadados = ImageMetadataReader.readMetadata(new ByteArrayInputStream(conteudo), conteudo.length);
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
