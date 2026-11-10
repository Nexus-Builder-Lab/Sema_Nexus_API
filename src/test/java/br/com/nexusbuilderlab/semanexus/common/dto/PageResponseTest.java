package br.com.nexusbuilderlab.semanexus.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    void converteUmaPageNoFormatoPadrao() {
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> r = PageResponse.of(page);

        assertThat(r.content()).containsExactly("a", "b");
        assertThat(r.page()).isEqualTo(1);
        assertThat(r.size()).isEqualTo(2);
        assertThat(r.totalElements()).isEqualTo(5);
        assertThat(r.totalPages()).isEqualTo(3);
    }

    @Test
    void aplicaOMapeadorAosItens() {
        var page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(0, 10), 3);

        PageResponse<String> r = PageResponse.of(page, n -> "DEM-" + n);

        assertThat(r.content()).containsExactly("DEM-1", "DEM-2", "DEM-3");
        assertThat(r.totalPages()).isEqualTo(1);
    }
}
