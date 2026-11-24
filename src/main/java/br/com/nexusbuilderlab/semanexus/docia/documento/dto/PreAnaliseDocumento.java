package br.com.nexusbuilderlab.semanexus.docia.documento.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** Metadados SUGERIDOS a partir do arquivo; nada é gravado — o usuário confirma e edita no formulário. */
@Schema(description = "Sugestões de metadados para o upload (nada é salvo)")
public record PreAnaliseDocumento(
        @Schema(description = "Título sugerido") String titulo,
        @Schema(description = "Município detectado no texto (código IBGE), se houver") Integer municipioId,
        @Schema(description = "Nome do município detectado") String municipioNome,
        @Schema(description = "Data do documento detectada no texto") LocalDate dataDocumento,
        @Schema(description = "Palavras-chave sugeridas") List<String> palavrasChave,
        @Schema(description = "Números DEM-AAAA/NNNN citados no texto", example = "[\"DEM-2026/0010\"]") List<String> vinculosDemanda,
        @Schema(description = "Houve texto extraído do arquivo") boolean textoExtraido,
        @Schema(description = "Avisos (ex.: OCR indisponível)") List<String> avisos) {
}
