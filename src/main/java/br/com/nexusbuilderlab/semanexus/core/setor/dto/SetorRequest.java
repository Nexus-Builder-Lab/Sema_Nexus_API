package br.com.nexusbuilderlab.semanexus.core.setor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cadastro/edição de setor. {@code ativo} é opcional (padrão: ativo no cadastro; inalterado na edição). */
public record SetorRequest(
        @NotBlank @Size(max = 20) String sigla,
        @NotBlank @Size(max = 150) String nome,
        Boolean ativo) {
}
