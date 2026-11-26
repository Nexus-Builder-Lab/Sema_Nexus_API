package br.com.nexusbuilderlab.semanexus.intelligence.camada;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CamadaPublicaRepositoryTest {

    @Test
    @SuppressWarnings("unchecked")
    void naCaixaFiltraPorCamadaENaoSelecionaClobAtributos() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CamadaPublicaRepository repo = new CamadaPublicaRepository(jdbc);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(15L);
        when(rs.getLong("IMPORTACAO_ID")).thenReturn(2L);
        when(rs.getString("CAMADA")).thenReturn("TERRA_INDIGENA");
        when(rs.getString("CODIGO_ORIGEM")).thenReturn("TI-15");
        when(rs.getString("NOME")).thenReturn("Terra Indígena Teste");
        when(rs.getString("CATEGORIA")).thenReturn("Regularizada");
        when(rs.getString("ESFERA")).thenReturn("Federal");
        when(rs.getBigDecimal("AREA_HA")).thenReturn(new BigDecimal("1200.50"));
        when(rs.getString("GEOMETRIA")).thenReturn("{\"type\":\"Polygon\"}");

        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    RowMapper<CamadaPublicaRepository.Feicao> mapper = inv.getArgument(1);
                    return List.of(mapper.mapRow(rs, 0));
                });

        var resultado = repo.naCaixa(TipoCamadaPublica.TERRA_INDIGENA, -16.0, -15.0, -57.0, -56.0, 2000);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(RowMapper.class),
                org.mockito.ArgumentMatchers.eq("TERRA_INDIGENA"),
                org.mockito.ArgumentMatchers.eq(-16.0),
                org.mockito.ArgumentMatchers.eq(-15.0),
                org.mockito.ArgumentMatchers.eq(-57.0),
                org.mockito.ArgumentMatchers.eq(-56.0),
                org.mockito.ArgumentMatchers.eq(2000));

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("WHERE CAMADA = ?");
        assertThat(sql).doesNotContain("ATRIBUTOS");
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).camada()).isEqualTo(TipoCamadaPublica.TERRA_INDIGENA);
        assertThat(resultado.get(0).atributos()).isNull();
        verify(rs, never()).getString("ATRIBUTOS");
    }
}
