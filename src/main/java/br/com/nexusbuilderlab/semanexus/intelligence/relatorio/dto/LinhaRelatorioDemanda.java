package br.com.nexusbuilderlab.semanexus.intelligence.relatorio.dto;

import java.time.LocalDate;

/** Linha do relatório de demandas (dados fictícios). */
public record LinhaRelatorioDemanda(
        String numero, String titulo, String categoria, String municipio, String bioma, String status,
        String prioridade, LocalDate dataRecebimento, LocalDate prazo, LocalDate concluidaEm) {
}
