package br.com.nexusbuilderlab.semanexus.docia.documento.dto;

import br.com.nexusbuilderlab.semanexus.common.dto.DemandaRef;
import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Detalhes do documento, com texto extraído e demandas vinculadas. */
@Schema(description = "Documento com metadados, texto extraído e demandas vinculadas")
public record DocumentoDetalhe(
        @Schema(example = "7") Long id,
        String titulo,
        @Schema(nullable = true) String descricao,
        @Schema(example = "Relatório técnico") String categoria,
        @Schema(example = "Laudo") String tipo,
        LocalDate dataDocumento,
        @Schema(nullable = true) MunicipioRef municipio,
        List<String> palavrasChave,
        String nomeArquivo,
        @Schema(example = "application/pdf") String mime,
        @Schema(description = "Tamanho em bytes", example = "184320") long tamanho,
        @Schema(description = "SHA-256 do arquivo (hex)") String sha256,
        @Schema(description = "Texto extraído (PDF, TXT, MD, CSV). Ausente quando não houve extração (ex.: DOCX no MVP)",
                nullable = true) String textoExtraido,
        @Schema(description = "Demandas às quais o documento está vinculado") List<DemandaRef> demandas,
        UsuarioRef responsavel,
        LocalDateTime criadoEm,
        @Schema(nullable = true) LocalDateTime atualizadoEm) {
}
