package br.com.nexusbuilderlab.semanexus.common.openapi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um endpoint cujo contrato está publicado no OpenAPI mas ainda responde {@code 501}.
 * O {@code ErrosPadraoCustomizer} documenta a resposta 501 e o aviso na operação; a implementação do
 * método deve lançar {@code ContratoPendenteException} com o mesmo número de Issue.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ContratoPendente {

    /** Número da Issue (deste repositório) que implementa o endpoint. */
    int issue();
}
