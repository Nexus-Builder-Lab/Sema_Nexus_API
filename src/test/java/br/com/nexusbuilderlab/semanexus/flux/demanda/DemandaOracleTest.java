package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nexusbuilderlab.semanexus.common.dto.PageResponse;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaFiltro;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaResumo;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Issue #11 contra Oracle real (Testcontainers): numeração sem colisão em cadastros concorrentes, filtros
 * combinados e paginação. Fora do build padrão: {@code mvn -B -ntp verify -Poracle}. Exige Docker.
 */
@Tag("oracle")
@Testcontainers
@SpringBootTest
class DemandaOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    static final int CUIABA = 5103403;
    static final int RONDONOPOLIS = 5107602;

    @Autowired
    DemandaService service;
    @Autowired
    UsuarioRepository usuarios;

    private UsuarioAutenticado novoAtor(Perfil perfil) {
        String email = perfil.name().toLowerCase() + "." + UUID.randomUUID() + "@exemplo.test";
        Usuario u = usuarios.save(new Usuario("Usuário de Teste", email, "hash-ficticio", perfil));
        return UsuarioAutenticado.de(u);
    }

    private static LocalDate hoje() {
        return LocalDate.now(ZoneId.of("America/Cuiaba"));
    }

    private DemandaDetalhe cadastrar(UsuarioAutenticado ator, String titulo, int municipio,
                                     CategoriaFiscalizacao categoria, Prioridade prioridade, LocalDate recebimento) {
        return service.cadastrar(ator, new DemandaRequest(titulo, "Descrição fictícia", Origem.DENUNCIA, categoria,
                municipio, null, null, null, recebimento, prioridade, null, null));
    }

    @Test
    void cadastrosConcorrentesRecebemNumerosDistintosESequenciais() throws Exception {
        UsuarioAutenticado ator = novoAtor(Perfil.ANALISTA);
        int total = 12;
        ExecutorService pool = Executors.newFixedThreadPool(total);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<String>> futuros = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            String titulo = "Concorrente " + i;
            futuros.add(pool.submit(() -> {
                largada.await();
                return cadastrar(ator, titulo, CUIABA, null, null, hoje()).numero();
            }));
        }
        largada.countDown();
        List<String> numeros = new ArrayList<>();
        for (Future<String> f : futuros) {
            numeros.add(f.get());
        }
        pool.shutdown();

        assertThat(numeros).doesNotHaveDuplicates().hasSize(total);
        String prefixo = "DEM-" + hoje().getYear() + "/";
        List<Integer> sequenciais = numeros.stream().peek(n -> assertThat(n).startsWith(prefixo))
                .map(n -> Integer.parseInt(n.substring(prefixo.length()))).sorted().collect(Collectors.toList());
        for (int i = 1; i < sequenciais.size(); i++) {
            assertThat(sequenciais.get(i)).isEqualTo(sequenciais.get(i - 1) + 1);
        }
    }

    @Test
    void filtrosCombinadosEPaginacao() {
        UsuarioAutenticado ator = novoAtor(Perfil.GESTOR);
        String marca = "Filtro" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate base = hoje();
        cadastrar(ator, marca + " A", CUIABA, CategoriaFiscalizacao.QUEIMADA_INCENDIO, Prioridade.ALTA, base.minusDays(1));
        cadastrar(ator, marca + " B", CUIABA, CategoriaFiscalizacao.QUEIMADA_INCENDIO, Prioridade.BAIXA, base.minusDays(2));
        cadastrar(ator, marca + " C", RONDONOPOLIS, CategoriaFiscalizacao.QUEIMADA_INCENDIO, Prioridade.ALTA, base.minusDays(3));
        cadastrar(ator, marca + " D", CUIABA, CategoriaFiscalizacao.FAUNA_SILVESTRE, Prioridade.ALTA, base.minusDays(10));
        cadastrar(ator, marca + " E", CUIABA, CategoriaFiscalizacao.QUEIMADA_INCENDIO, Prioridade.ALTA, base.minusDays(20));

        // texto isola os 5 registros do teste; sem mais filtros, pagina em 2 + 2 + 1
        PageResponse<DemandaResumo> p0 = service.listar(ator, filtro(marca, null, null, null, null, null, null, null),
                PageRequest.of(0, 2));
        assertThat(p0.totalElements()).isEqualTo(5);
        assertThat(p0.totalPages()).isEqualTo(3);
        assertThat(p0.content()).hasSize(2);
        // ordenação padrão: data de recebimento decrescente (A, B, C, D, E)
        assertThat(p0.content()).extracting(DemandaResumo::titulo).containsExactly(marca + " A", marca + " B");
        PageResponse<DemandaResumo> p2 = service.listar(ator, filtro(marca, null, null, null, null, null, null, null),
                PageRequest.of(2, 2));
        assertThat(p2.content()).extracting(DemandaResumo::titulo).containsExactly(marca + " E");

        // município + categoria + prioridade
        assertThat(titulos(service.listar(ator, filtro(marca, CUIABA, CategoriaFiscalizacao.QUEIMADA_INCENDIO,
                Prioridade.ALTA, null, null, null, null), PageRequest.of(0, 10))))
                .containsExactlyInAnyOrder(marca + " A", marca + " E");

        // + intervalo de datas de recebimento
        assertThat(titulos(service.listar(ator, filtro(marca, CUIABA, CategoriaFiscalizacao.QUEIMADA_INCENDIO,
                Prioridade.ALTA, null, base.minusDays(5), base, null), PageRequest.of(0, 10))))
                .containsExactly(marca + " A");

        // status (todas PENDENTE) e "atrasada" (nenhuma tem prazo)
        assertThat(service.listar(ator, filtro(marca, null, null, null, StatusDemanda.PENDENTE, null, null, null),
                PageRequest.of(0, 10)).totalElements()).isEqualTo(5);
        assertThat(service.listar(ator, filtro(marca, null, null, null, StatusDemanda.FINALIZADA, null, null, null),
                PageRequest.of(0, 10)).totalElements()).isZero();
        assertThat(service.listar(ator, filtro(marca, null, null, null, null, null, null, true),
                PageRequest.of(0, 10)).totalElements()).isZero();
        assertThat(service.listar(ator, filtro(marca, null, null, null, null, null, null, false),
                PageRequest.of(0, 10)).totalElements()).isEqualTo(5);
    }

    @Test
    void fiscalSemEquipeNaoEnxergaDemandasDeOutros() {
        UsuarioAutenticado gestor = novoAtor(Perfil.GESTOR);
        UsuarioAutenticado fiscal = novoAtor(Perfil.FISCAL);
        String marca = "Escopo" + UUID.randomUUID().toString().substring(0, 8);
        cadastrar(gestor, marca, CUIABA, null, null, hoje());

        assertThat(service.listar(gestor, filtro(marca, null, null, null, null, null, null, null),
                PageRequest.of(0, 10)).totalElements()).isEqualTo(1);
        assertThat(service.listar(fiscal, filtro(marca, null, null, null, null, null, null, null),
                PageRequest.of(0, 10)).totalElements()).isZero();
    }

    @Test
    void edicaoEExclusaoComLockPessimistaFuncionamNoOracle() {
        UsuarioAutenticado ator = novoAtor(Perfil.ADMIN);
        DemandaDetalhe criada = cadastrar(ator, "Para editar e excluir", CUIABA, null, null, hoje());

        DemandaDetalhe editada = service.editar(ator, criada.id(), new DemandaRequest("Título editado", "Descrição fictícia",
                Origem.DENUNCIA, null, CUIABA, null, null, null, hoje(), null, null, null));
        assertThat(editada.titulo()).isEqualTo("Título editado");
        service.excluir(ator, criada.id());
        assertThat(service.listar(ator, filtro("Título editado", null, null, null, null, null, null, null),
                PageRequest.of(0, 10)).content()).extracting(DemandaResumo::id).doesNotContain(criada.id());
    }

    private static DemandaFiltro filtro(String q, Integer municipio, CategoriaFiscalizacao categoria,
                                        Prioridade prioridade, StatusDemanda status, LocalDate de, LocalDate ate,
                                        Boolean atrasada) {
        return new DemandaFiltro(q, status, municipio, categoria, prioridade, null, null, atrasada, de, ate);
    }

    private static List<String> titulos(PageResponse<DemandaResumo> pagina) {
        return pagina.content().stream().map(DemandaResumo::titulo).toList();
    }
}
