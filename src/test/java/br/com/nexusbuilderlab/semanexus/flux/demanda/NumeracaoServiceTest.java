package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class NumeracaoServiceTest {

    @Mock
    NumeracaoDemandaRepository repositorio;
    @Mock
    PlatformTransactionManager transacoes;

    @Test
    void formataComoDemAnoBarraSequencialComQuatroDigitos() {
        assertThat(NumeracaoService.formatar(2026, 1)).isEqualTo("DEM-2026/0001");
        assertThat(NumeracaoService.formatar(2026, 12)).isEqualTo("DEM-2026/0012");
        assertThat(NumeracaoService.formatar(2027, 12345)).isEqualTo("DEM-2027/12345");
    }

    @Test
    void numerosSaoSequenciaisNoMesmoAno() {
        NumeracaoDemanda linha = new NumeracaoDemanda(2026);
        when(repositorio.buscarComLock(2026)).thenReturn(Optional.of(linha));
        NumeracaoService service = new NumeracaoService(repositorio, transacoes);

        assertThat(service.proximoNumero(2026)).isEqualTo("DEM-2026/0001");
        assertThat(service.proximoNumero(2026)).isEqualTo("DEM-2026/0002");
        assertThat(service.proximoNumero(2026)).isEqualTo("DEM-2026/0003");
        assertThat(linha.getUltimo()).isEqualTo(3);
    }

    @Test
    void primeiroNumeroDoAnoCriaALinhaEIniciaASequencia() {
        NumeracaoDemanda criada = new NumeracaoDemanda(2027);
        when(repositorio.buscarComLock(2027)).thenReturn(Optional.empty(), Optional.of(criada));
        NumeracaoService service = new NumeracaoService(repositorio, transacoes);

        assertThat(service.proximoNumero(2027)).isEqualTo("DEM-2027/0001");
        verify(repositorio).saveAndFlush(any(NumeracaoDemanda.class));
    }
}
