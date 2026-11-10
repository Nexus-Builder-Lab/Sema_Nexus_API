package br.com.nexusbuilderlab.semanexus.docia.documento.dto;

import br.com.nexusbuilderlab.semanexus.common.dto.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.common.dto.UsuarioRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Item da biblioteca documental (sem o texto extraído, que só vem nos detalhes). */
@Schema(description = "Documento na listagem da biblioteca (dados fictícios na demonstração)")
public record DocumentoResumo(
        @Schema(example = "7") Long id,
        String titulo,
        @Schema(example = "Relatório técnico") String categoria,
        @Schema(example = "Laudo") String tipo,
        LocalDate dataDocumento,
        @Schema(nullable = true) MunicipioRef municipio,
        List<String> palavrasChave,
        @Schema(description = "Nome original do arquivo (só metadado; o arquivo fica no volume)", example = "relatorio-vistoria.pdf")
        String nomeArquivo,
        @Schema(example = "application/pdf") String mime,
        @Schema(description = "Tamanho em bytes", example = "184320") long tamanho,
        UsuarioRef responsavel,
        LocalDateTime criadoEm) {
}
