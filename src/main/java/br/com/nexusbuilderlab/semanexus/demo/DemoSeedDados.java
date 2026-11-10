package br.com.nexusbuilderlab.semanexus.demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Conteúdo de {@code demo/usuarios.json}. Dados exclusivamente fictícios; senhas só como hash BCrypt. */
@JsonIgnoreProperties(ignoreUnknown = true)
record DemoSeedDados(List<SetorSeed> setores, List<UsuarioSeed> usuarios, List<EquipeSeed> equipes) {

    record SetorSeed(String sigla, String nome) {
    }

    record UsuarioSeed(String nome, String email, String perfil, String setor, String senhaHash) {

        /** Nunca expõe o hash em logs ou mensagens de erro. */
        @Override
        public String toString() {
            return "UsuarioSeed[email=" + email + ", perfil=" + perfil + "]";
        }
    }

    record EquipeSeed(String nome, String setor, String responsavel, List<String> membros) {
    }
}
