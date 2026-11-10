package br.com.nexusbuilderlab.semanexus.docia.documento.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

/**
 * Corpo do upload ({@code multipart/form-data}): o arquivo na parte {@code arquivo} e os metadados nos demais
 * campos. O responsável é sempre o usuário autenticado. Extensões aceitas: pdf, txt, md, csv, docx; máximo de 10 MB.
 */
@Schema(description = "Arquivo e metadados do documento enviado (multipart/form-data)")
public record DocumentoUploadRequest(
        @Schema(description = "Arquivo (pdf, txt, md, csv ou docx; até 10 MB)", type = "string", format = "binary")
        @NotNull MultipartFile arquivo,
        @Schema(description = "Título (3 a 200 caracteres)", example = "Relatório de vistoria — Fazenda Modelo (fictício)")
        @NotBlank @Size(min = 3, max = 200) String titulo,
        @Schema(nullable = true) @Size(max = 2000) String descricao,
        @Schema(description = "Categoria documental (texto livre, até 60 caracteres)", example = "Relatório técnico")
        @NotBlank @Size(max = 60) String categoria,
        @Schema(description = "Tipo do documento (texto livre, até 60 caracteres)", example = "Laudo")
        @NotBlank @Size(max = 60) String tipo,
        @Schema(description = "Data do documento (não pode ser futura)", example = "2026-11-05")
        @NotNull @PastOrPresent @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDocumento,
        @Schema(description = "Código IBGE de um município de MT", example = "5103403", nullable = true)
        Integer municipioId,
        @Schema(description = "Palavras-chave (até 20; repita o campo ou separe por vírgula)", example = "[\"desmatamento\",\"vistoria\"]",
                nullable = true)
        @Size(max = 20) List<@Size(max = 50) String> palavrasChave,
        @Schema(description = "Se informado, já vincula o documento a esta demanda", example = "10", nullable = true)
        Long demandaId) {
}
