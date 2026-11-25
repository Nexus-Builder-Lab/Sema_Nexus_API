package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Metadados de uma foto de evidência da fiscalização (Issue #145). O arquivo é baixado por endpoint próprio. */
@Schema(description = "Foto de evidência da fiscalização (demonstrativa). EXIF é declarado pelo arquivo: informativo, "
        + "sem valor de prova.")
public record FotoFiscalizacao(
        @Schema(example = "7") Long id,
        @Schema(example = "42") Long demandaId,
        @Schema(description = "Nome original saneado (não é usado no disco)", example = "vistoria-01.jpg")
        String nomeOriginal,
        @Schema(example = "image/jpeg") String mime,
        @Schema(description = "Tamanho em bytes", example = "184233") long tamanho,
        @Schema(description = "SHA-256 do conteúdo (hex)") String sha256,
        @Schema(description = "Data/hora da captura no EXIF (horário local da câmera)", nullable = true)
        LocalDateTime exifDataHora,
        @Schema(description = "Latitude do GPS no EXIF", nullable = true, example = "-15.601") BigDecimal exifLatitude,
        @Schema(description = "Longitude do GPS no EXIF", nullable = true, example = "-56.097") BigDecimal exifLongitude,
        @Schema(description = "Distância (km) do GPS até a coordenada da demanda, ou até a borda da área (0 = dentro)",
                nullable = true, example = "0.42")
        BigDecimal distanciaKm,
        @Schema(description = "GPS do EXIF fora da caixa envolvente de Mato Grosso") boolean gpsForaMt,
        @Schema(description = "Selo de conferência do EXIF") ConferenciaExif conferencia,
        UsuarioRef autor,
        LocalDateTime criadoEm) {

    /**
     * Resultado da conferência do GPS do EXIF com o local da demanda: {@code CONFERE} (até
     * {@code LIMITE_CONFERE_KM}), {@code DIVERGENTE}, {@code FORA_MT}, {@code SEM_GPS} ou {@code SEM_REFERENCIA}
     * (demanda sem coordenada nem área).
     */
    public enum ConferenciaExif { CONFERE, DIVERGENTE, FORA_MT, SEM_GPS, SEM_REFERENCIA }
}
