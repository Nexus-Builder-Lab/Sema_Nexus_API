package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Validações declarativas do DTO: título 5-200, retângulo de MT e data de recebimento não futura. */
class DemandaRequestValidacaoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static DemandaRequest pedido(String titulo, String lat, String lng, LocalDate data) {
        return new DemandaRequest(titulo, "Descrição fictícia", Origem.DENUNCIA, null, 5103403, null,
                lat == null ? null : new BigDecimal(lat), lng == null ? null : new BigDecimal(lng), null, null, data, null, null, null);
    }

    private Set<String> campos(DemandaRequest p) {
        return validator.validate(p).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void pedidoValidoNaoTemViolacoes() {
        assertThat(campos(pedido("Título válido", "-15.6", "-56.1", LocalDate.now()))).isEmpty();
    }

    @Test
    void coordenadasForaDoRetanguloDeMtViolamPorCampo() {
        assertThat(campos(pedido("Título válido", "-5.0", "-56.1", LocalDate.now()))).containsExactly("latitude");
        assertThat(campos(pedido("Título válido", "-20.0", "-56.1", LocalDate.now()))).containsExactly("latitude");
        assertThat(campos(pedido("Título válido", "-15.6", "-49.0", LocalDate.now()))).containsExactly("longitude");
        assertThat(campos(pedido("Título válido", "-15.6", "-62.0", LocalDate.now()))).containsExactly("longitude");
    }

    @Test
    void tituloForaDe5a200ViolaEDataFuturaViola() {
        assertThat(campos(pedido("abc", null, null, LocalDate.now()))).containsExactly("titulo");
        assertThat(campos(pedido("x".repeat(201), null, null, LocalDate.now()))).containsExactly("titulo");
        assertThat(campos(pedido("Título válido", null, null, LocalDate.now().plusDays(1))))
                .containsExactly("dataRecebimento");
    }
}
