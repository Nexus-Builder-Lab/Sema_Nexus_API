package br.com.nexusbuilderlab.semanexus.flux.agente;

import java.time.LocalDate;
import java.util.List;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DecisaoTriagem;

import io.swagger.v3.oas.annotations.media.Schema;

/** Sugestão do Agente de Triagem. Nada é gravado: o usuário revisa e confirma pelo {@code POST /triagem}. */
@Schema(description = "Sugestão do Agente de Triagem (IA local, Claude ou regras locais), sempre revisada por uma pessoa")
public record SugestaoTriagem(
        @Schema(description = "LOCAL (IA local), CLAUDE ou DEMONSTRATIVO (regras locais)", example = "LOCAL") String modo,
        @Schema(example = "qwen3:4b-instruct") String modelo,
        DecisaoTriagem decisao,
        @Schema(nullable = true) CategoriaFiscalizacao categoria,
        @Schema(nullable = true) Prioridade prioridade,
        @Schema(description = "Núcleo regional sugerido pela região do município", nullable = true) Nucleo nucleo,
        @Schema(description = "Texto pronto para a justificativa da triagem") String justificativa,
        @Schema(description = "Demandas abertas que podem tratar do mesmo fato") List<Duplicata> duplicatas,
        @Schema(description = "O que o agente consultou para chegar à sugestão") List<String> evidencias,
        @Schema(description = "Avisos (ex.: IA indisponível, campo completado pelas regras locais)") List<String> avisos,
        @Schema(description = "Evidência espacial para exibição (distâncias em km, raio, contagens e documentos relacionados)", nullable = true) EvidenciaEspacial espacial) {


    @Schema(description = "Evidência espacial (raio em torno da coordenada/área da demanda); nula só quando não foi calculada",
            nullable = true)
    public record EvidenciaEspacial(
            @Schema(description = "Ponto de referência: COORDENADA, AREA (centroide de areaGeometria) ou MUNICIPIO (fallback sem coordenada)",
                    example = "COORDENADA") String referencia,
            @Schema(nullable = true) Double latitude,
            @Schema(nullable = true) Double longitude,
            @Schema(description = "Raio da análise em km", example = "10") double raioKm,
            @Schema(description = "Focos de calor e alertas DETER do INPE (dados públicos)") Inpe inpe,
            @Schema(description = "Demandas não finalizadas no raio (ou no município, no fallback), da mais próxima à mais distante") List<Proxima> demandasProximas,
            @Schema(description = "Documentos ativos da mesma localidade/município ou vinculados a demandas próximas") List<DocumentoRelacionado> documentos) {
    }

    public record Inpe(
            @Schema(description = "Focos de calor no raio (0 no fallback: use focosMunicipio)") long focosNoRaio,
            @Schema(description = "Distância ao foco mais próximo, km", nullable = true) Double focoMaisProximoKm,
            @Schema(description = "Alertas DETER cujo polígono toca o raio") long alertasDeterNoRaio,
            @Schema(description = "O ponto está dentro de algum polígono DETER") boolean dentroDePoligonoDeter,
            @Schema(description = "Menor distância (km) do ponto a um polígono DETER no raio; 0 se dentro", nullable = true) Double menorDistanciaDeterKm,
            @Schema(description = "Focos de calor no município (contexto)") long focosMunicipio,
            @Schema(description = "Alertas DETER no município (contexto)") long alertasDeterMunicipio) {
    }

    public record Proxima(Long id, String numero, String titulo, StatusDemanda status,
                          @Schema(nullable = true) CategoriaFiscalizacao categoria, String municipio,
                          @Schema(description = "Distância em km (nula no fallback ao município)", nullable = true) Double distanciaKm) {
    }

    public record DocumentoRelacionado(Long id, String titulo,
                                       @Schema(description = "Por que foi relacionado", example = "Mesmo município") String motivo) {
    }
    @Schema(description = "Núcleo Regional de Fiscalização (fictício)")
    public record Nucleo(@Schema(example = "NR-SNO") String sigla, @Schema(example = "Núcleo Regional de Sinop") String nome) {
    }

    @Schema(description = "Possível duplicata")
    public record Duplicata(Long id, String numero, String titulo, StatusDemanda status, LocalDate dataRecebimento,
                            @Schema(description = "Distancia em km quando achada por proximidade espacial", nullable = true) Double distanciaKm) {
    }
}
