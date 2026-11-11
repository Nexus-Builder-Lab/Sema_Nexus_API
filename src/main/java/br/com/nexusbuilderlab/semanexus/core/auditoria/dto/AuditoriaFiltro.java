package br.com.nexusbuilderlab.semanexus.core.auditoria.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Filtros combináveis de {@code GET /api/auditoria}; {@code de}/{@code ate} são datas inclusivas (AAAA-MM-DD). */
public record AuditoriaFiltro(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
        Long usuarioId,
        String acao,
        String entidade) {
}
