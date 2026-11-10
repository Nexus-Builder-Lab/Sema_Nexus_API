package br.com.nexusbuilderlab.semanexus.docia.documento.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Filtros opcionais e combináveis da biblioteca documental (query string). */
public record DocumentoFiltro(
        @Schema(description = "Pesquisa textual em título, descrição, palavras-chave e conteúdo extraído "
                + "(busca textual — não é busca semântica)") String q,
        @Schema(description = "Categoria documental (igualdade, sem diferenciar maiúsculas)") String categoria,
        @Schema(description = "Tipo do documento (igualdade, sem diferenciar maiúsculas)") String tipo,
        @Schema(description = "Código IBGE do município", example = "5103403") Integer municipioId,
        @Schema(description = "Somente documentos vinculados a esta demanda") Long demandaId,
        @Schema(description = "Data do documento a partir de (inclusive)", example = "2026-01-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
        @Schema(description = "Data do documento até (inclusive)", example = "2026-12-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
}
