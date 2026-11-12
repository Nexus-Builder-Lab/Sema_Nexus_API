package br.com.nexusbuilderlab.semanexus.core.auth.dto;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import io.swagger.v3.oas.annotations.media.Schema;

/** Cartão do "Modo demonstração" da tela de login (sem credenciais). */
public record PerfilDemo(
        @Schema(example = "GESTOR") Perfil perfil,
        @Schema(example = "Gestor") String nome,
        @Schema(example = "Acompanha demandas, equipes e indicadores.") String descricao) {
}
