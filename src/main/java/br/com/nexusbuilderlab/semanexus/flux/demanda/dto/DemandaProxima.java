package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

/** Demanda não finalizada encontrada num raio, com a distância (Haversine) até o ponto consultado. */
public record DemandaProxima(DemandaResumo demanda, double distanciaKm) {
}
