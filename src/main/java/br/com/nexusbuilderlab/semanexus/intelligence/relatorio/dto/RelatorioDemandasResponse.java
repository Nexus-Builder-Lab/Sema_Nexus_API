package br.com.nexusbuilderlab.semanexus.intelligence.relatorio.dto;

import java.util.List;

/** Relatório paginado de demandas, com o aviso demonstrativo. */
public record RelatorioDemandasResponse(
        boolean demonstrativo, String aviso, List<LinhaRelatorioDemanda> content, int page, int size,
        long totalElements, int totalPages) {
}
