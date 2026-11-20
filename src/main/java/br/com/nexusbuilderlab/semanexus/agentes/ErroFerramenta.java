package br.com.nexusbuilderlab.semanexus.agentes;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;

import org.springframework.security.access.AccessDeniedException;

/**
 * Catálogo de erros públicos das ferramentas dos agentes (relatório técnico, P2 "mensagens de erro de ferramenta").
 * O modelo, os passos exibidos na interface e a auditoria recebem só o código e a mensagem pública do catálogo, mais
 * uma referência de correlação; o detalhe técnico ({@code e.getMessage()}, que numa ferramenta futura pode trazer
 * caminho, SQL ou credencial) fica apenas no log da API, com a mesma referência.
 */
public enum ErroFerramenta {

    ACESSO_NEGADO("O perfil do usuário não tem acesso a este registro."),
    NAO_ENCONTRADO("Registro não encontrado ou inativo."),
    PARAMETRO_INVALIDO("Parâmetro inválido para a ferramenta."),
    FERRAMENTA_INEXISTENTE("A ferramenta pedida não existe para este agente."),
    ERRO_INTERNO("Falha interna ao executar a ferramenta.");

    private static final Pattern NOME_SEGURO = Pattern.compile("[^a-z0-9_]");

    private final String mensagemPublica;

    ErroFerramenta(String mensagemPublica) {
        this.mensagemPublica = mensagemPublica;
    }

    public String mensagemPublica() {
        return mensagemPublica;
    }

    /** Classifica a exceção pelo tipo, nunca pela mensagem. */
    public static ErroFerramenta de(Throwable e) {
        if (e instanceof AccessDeniedException) {
            return ACESSO_NEGADO;
        }
        if (e instanceof RecursoNaoEncontradoException) {
            return NAO_ENCONTRADO;
        }
        if (e instanceof IllegalArgumentException || e instanceof DadosInvalidosException) {
            return PARAMETRO_INVALIDO;
        }
        return ERRO_INTERNO;
    }

    /** Texto público: código, mensagem do catálogo e a referência que liga ao log. */
    public String publico(String referencia) {
        return name() + ": " + mensagemPublica + " (ref. " + referencia + ")";
    }

    /** Referência curta de correlação entre o passo/auditoria e o log restrito. */
    public static String novaReferencia() {
        return "ERR-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Nome pedido pelo modelo reduzido a caracteres seguros (é ecoado nos passos). */
    public static String nomeSeguro(String nome) {
        String n = NOME_SEGURO.matcher(nome == null ? "" : nome.toLowerCase(Locale.ROOT)).replaceAll("");
        return n.length() > 40 ? n.substring(0, 40) : n;
    }
}
