package br.com.nexusbuilderlab.semanexus.intelligence.referencia;

import br.com.nexusbuilderlab.semanexus.core.municipio.Bioma;
import java.time.LocalDate;

/** Filtros da consulta de pontos (de/ate inclusivos; qualquer campo pode ser nulo). */
public record ReferenciaFiltro(TipoReferencia tipo, LocalDate de, LocalDate ate, Integer municipioId, Bioma bioma) {
}
