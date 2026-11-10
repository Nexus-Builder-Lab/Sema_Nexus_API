package br.com.nexusbuilderlab.semanexus.common.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Formato padrão de resposta paginada da API.
 *
 * <p>Requisição: {@code ?page=0&size=20&sort=campo,asc}. Resposta:
 * {@code {content, page, size, totalElements, totalPages}}.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<? super E, ? extends T> mapper) {
        return of(page.map(mapper));
    }
}
