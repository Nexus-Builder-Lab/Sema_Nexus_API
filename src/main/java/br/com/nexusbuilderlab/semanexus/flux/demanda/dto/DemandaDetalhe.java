package br.com.nexusbuilderlab.semanexus.flux.demanda.dto;

import br.com.nexusbuilderlab.semanexus.common.dto.EquipeRef;
import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Dados completos da demanda. É também a resposta das operações de fluxo (triagem, distribuição, início,
 * fiscalização, conclusão e devolução), que devolvem a demanda já no novo status.
 */
@Schema(description = "Demanda de fiscalização com todos os dados (dados fictícios na demonstração)")
public record DemandaDetalhe(
        @Schema(example = "10") Long id,
        @Schema(description = "Número DEM-AAAA/NNNN, gerado no cadastro", example = "DEM-2026/0010") String numero,
        String titulo,
        String descricao,
        Origem origem,
        @Schema(nullable = true) CategoriaFiscalizacao categoria,
        MunicipioRef municipio,
        @Schema(nullable = true) String localidade,
        @Schema(nullable = true) BigDecimal latitude,
        @Schema(nullable = true) BigDecimal longitude,
        LocalDate dataRecebimento,
        @Schema(nullable = true) Prioridade prioridade,
        @Schema(description = "Prazo definido na distribuição", nullable = true) LocalDate prazo,
        StatusDemanda status,
        @Schema(description = "Derivada: prazo < hoje e status ≠ FINALIZADA", example = "false") boolean atrasada,
        @Schema(nullable = true) EquipeRef equipe,
        @Schema(nullable = true) UsuarioRef fiscal,
        @Schema(nullable = true) String observacoes,
        @Schema(description = "Preenchido na conclusão", nullable = true) String resultadoFinal,
        @Schema(description = "Preenchida na conclusão", nullable = true) String justificativaConclusao,
        @Schema(nullable = true) LocalDateTime concluidaEm,
        @Schema(description = "Registro de fiscalização, quando já feito", nullable = true) FiscalizacaoDetalhe fiscalizacao,
        @Schema(description = "Documentos associados", example = "2") int totalDocumentos,
        UsuarioRef criadoPor,
        LocalDateTime criadoEm,
        @Schema(nullable = true) LocalDateTime atualizadoEm) {
}
