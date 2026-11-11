package br.com.nexusbuilderlab.semanexus.common.exception;

import br.com.nexusbuilderlab.semanexus.common.exception.GlobalExceptionHandler.ErroCampo;
import java.util.List;

/**
 * Regra de validação de negócio violada em campos específicos (ex.: município fora de MT, prazo anterior ao
 * recebimento). Resulta em HTTP 400 no mesmo formato da validação de campos ({@code errors[].campo/mensagem}).
 */
public class DadosInvalidosException extends RuntimeException {

    private final List<ErroCampo> erros;

    public DadosInvalidosException(List<ErroCampo> erros) {
        super("Um ou mais campos são inválidos.");
        this.erros = List.copyOf(erros);
    }

    public DadosInvalidosException(String campo, String mensagem) {
        this(List.of(new ErroCampo(campo, mensagem)));
    }

    public List<ErroCampo> getErros() {
        return erros;
    }
}
