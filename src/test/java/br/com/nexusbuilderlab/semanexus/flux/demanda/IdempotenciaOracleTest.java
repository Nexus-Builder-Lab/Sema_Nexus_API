package br.com.nexusbuilderlab.semanexus.flux.demanda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Prioridade;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.setor.SetorRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.demanda.IdempotenciaSincronizacaoService.ResultadoIdempotente;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.AtividadeRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DecisaoTriagem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DistribuicaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FiscalizacaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.HistoricoItem;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TriagemRequest;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Issue #147 contra Oracle 23ai real (V15): idempotência em início, atividade e fiscalização, concorrência serializada
 * pelo lock pessimista em {@code DEMANDA}, isolamento por usuário/demanda/operação, conflito 409 e constraints da
 * tabela {@code CHAVE_IDEMPOTENCIA}. Fora do build padrão: {@code mvn -B -ntp verify -Poracle}.
 */
@Tag("oracle")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "semanexus.jwt.secret=segredo-ficticio-somente-para-teste-0123456789")
class IdempotenciaOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    static final int CUIABA = 5103403;

    @Autowired JdbcTemplate jdbc;
    @Autowired DemandaService demandaService;
    @Autowired TriagemService triagemService;
    @Autowired DistribuicaoService distribuicaoService;
    @Autowired IdempotenciaSincronizacaoService idempotencia;
    @Autowired SetorRepository setores;
    @Autowired EquipeRepository equipes;
    @Autowired UsuarioRepository usuarios;

    private static LocalDate hoje() {
        return LocalDate.now(ZoneId.of("America/Cuiaba"));
    }

    private record Cenario(UsuarioAutenticado gestor, UsuarioAutenticado fiscal, Long demandaDistribuidaId) {
    }

    private Cenario prepararCenario() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Setor setor = setores.save(new Setor("IDEMP-" + sufixo.toUpperCase(), "Setor Idempotência " + sufixo));
        Usuario gestor = usuarios.save(new Usuario("Gestor " + sufixo, "g." + sufixo + "@exemplo.test", "hash", Perfil.GESTOR));
        Usuario fiscal = usuarios.save(new Usuario("Fiscal " + sufixo, "f." + sufixo + "@exemplo.test", "hash", Perfil.FISCAL));
        Equipe equipe = new Equipe("Equipe " + sufixo, setor);
        equipe.getMembros().add(fiscal);
        equipe = equipes.save(equipe);

        UsuarioAutenticado atorGestor = UsuarioAutenticado.de(gestor);
        DemandaDetalhe criada = demandaService.cadastrar(atorGestor, new DemandaRequest(
                "Demanda campo " + sufixo, "Descrição fictícia", Origem.DENUNCIA, null,
                CUIABA, null, null, null, null, null, hoje(), null, null, null));
        triagemService.iniciar(atorGestor, criada.id());
        triagemService.triar(atorGestor, criada.id(), new TriagemRequest(
                DecisaoTriagem.CLASSIFICAR, CategoriaFiscalizacao.QUEIMADA_INCENDIO, Prioridade.ALTA,
                "Classificada para campo."));
        distribuicaoService.distribuir(atorGestor, criada.id(),
                new DistribuicaoRequest(equipe.getId(), fiscal.getId(), hoje().plusDays(5), null));
        return new Cenario(atorGestor, UsuarioAutenticado.de(fiscal), criada.id());
    }

    @Test
    void fluxoCompletoComReenvioNaoDuplicaRegistrosERespeitaEscopo() {
        Cenario c = prepararCenario();
        long demandaId = c.demandaDistribuidaId();

        // 1) Início com replay
        ResultadoIdempotente<DemandaDetalhe> ini1 = idempotencia.iniciar(c.fiscal(), demandaId, "ini-" + demandaId);
        ResultadoIdempotente<DemandaDetalhe> ini2 = idempotencia.iniciar(c.fiscal(), demandaId, "ini-" + demandaId);
        assertThat(ini1.repetida()).isFalse();
        assertThat(ini2.repetida()).isTrue();
        assertThat(ini2.corpo().status()).isEqualTo(StatusDemanda.EM_ANDAMENTO);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM DEMANDA_HISTORICO WHERE DEMANDA_ID = ? AND TIPO = 'INICIO'",
                Long.class, demandaId)).isEqualTo(1L);

        // 2) Atividade: mesma chave não duplica; outra chave cria outro registro; mesmo key de outro usuário também
        AtividadeRequest ativ = new AtividadeRequest("Vistoria em campo", hoje());
        ResultadoIdempotente<HistoricoItem> a1 =
                idempotencia.registrarAtividade(c.fiscal(), demandaId, ativ, "ativ-1");
        ResultadoIdempotente<HistoricoItem> a2 =
                idempotencia.registrarAtividade(c.fiscal(), demandaId, ativ, "ativ-1");
        ResultadoIdempotente<HistoricoItem> a3 =
                idempotencia.registrarAtividade(c.fiscal(), demandaId, ativ, "ativ-2");
        ResultadoIdempotente<HistoricoItem> aGestor =
                idempotencia.registrarAtividade(c.gestor(), demandaId, ativ, "ativ-1");

        assertThat(a1.repetida()).isFalse();
        assertThat(a2.repetida()).isTrue();
        assertThat(a2.corpo().id()).isEqualTo(a1.corpo().id());
        assertThat(a3.repetida()).isFalse();
        assertThat(a3.corpo().id()).isNotEqualTo(a1.corpo().id());
        assertThat(aGestor.repetida()).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM DEMANDA_HISTORICO WHERE DEMANDA_ID = ? AND TIPO = 'ATIVIDADE'",
                Long.class, demandaId)).isEqualTo(3L);

        // Conflito 409 com outro payload na mesma chave
        assertThatThrownBy(() -> idempotencia.registrarAtividade(
                c.fiscal(), demandaId, new AtividadeRequest("Outro texto", hoje()), "ativ-1"))
                .isInstanceOf(ConflitoException.class);

        // 3) Fiscalização: 1a execução move para AGUARDANDO_CONCLUSAO; reenvio devolve o mesmo 200 sem duplicar
        FiscalizacaoRequest fisc = new FiscalizacaoRequest(hoje(), "Vistoria realizada", "Regular", "Fotos", null);
        ResultadoIdempotente<DemandaDetalhe> f1 =
                idempotencia.registrarFiscalizacao(c.fiscal(), demandaId, fisc, "fisc-1");
        ResultadoIdempotente<DemandaDetalhe> f2 =
                idempotencia.registrarFiscalizacao(c.fiscal(), demandaId, fisc, "fisc-1");

        assertThat(f1.repetida()).isFalse();
        assertThat(f1.corpo().status()).isEqualTo(StatusDemanda.AGUARDANDO_CONCLUSAO);
        assertThat(f2.repetida()).isTrue();
        assertThat(f2.corpo().status()).isEqualTo(StatusDemanda.AGUARDANDO_CONCLUSAO);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM DEMANDA_HISTORICO WHERE DEMANDA_ID = ? AND TIPO = 'FISCALIZACAO'",
                Long.class, demandaId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM AUDITORIA WHERE ENTIDADE = 'DEMANDA' AND ENTIDADE_ID = ? AND ACAO = 'REGISTRAR_FISCALIZACAO'",
                Long.class, String.valueOf(demandaId))).isEqualTo(1L);
    }

    @Test
    void requisicoesConcorrentesComMesmaChaveNaoDuplicamNoOracle() throws Exception {
        Cenario c = prepararCenario();
        long demandaId = c.demandaDistribuidaId();
        idempotencia.iniciar(c.fiscal(), demandaId, "ini-conc-" + demandaId);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        AtividadeRequest ativ = new AtividadeRequest("Atividade concorrente", hoje());
        List<Future<ResultadoIdempotente<HistoricoItem>>> futuros = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futuros.add(pool.submit(() -> {
                largada.await();
                return idempotencia.registrarAtividade(c.fiscal(), demandaId, ativ, "conc-key-1");
            }));
        }
        largada.countDown();

        List<ResultadoIdempotente<HistoricoItem>> resultados = new ArrayList<>();
        for (var f : futuros) {
            resultados.add(f.get());
        }
        pool.shutdown();

        assertThat(resultados.stream().filter(r -> !r.repetida()).count()).isEqualTo(1L);
        assertThat(resultados.stream().filter(ResultadoIdempotente::repetida).count()).isEqualTo(threads - 1L);
        assertThat(resultados.stream().map(r -> r.corpo().id()).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM DEMANDA_HISTORICO WHERE DEMANDA_ID = ? AND TIPO = 'ATIVIDADE'",
                Long.class, demandaId)).isEqualTo(1L);
    }

    @Test
    void restricoesDaV15() {
        Cenario c = prepararCenario();
        long uid = c.fiscal().id();
        long did = c.demandaDistribuidaId();
        String insert = "INSERT INTO CHAVE_IDEMPOTENCIA (USUARIO_ID, DEMANDA_ID, OPERACAO, CHAVE, HASH_REQUISICAO, "
                + "STATUS_HTTP, CORPO_RESPOSTA, EXPIRA_EM) VALUES (?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP + 1)";
        String hash = "a".repeat(64);
        assertThatThrownBy(() -> jdbc.update(insert, uid, did, "INVALIDA", "k1", hash, 200, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, uid, did, "ATIVIDADE", "chave com espaco", hash, 201, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, uid, did, "ATIVIDADE", "k2", "curto", 201, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, uid, did, "ATIVIDADE", "k3", hash, 500, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, uid, did, "ATIVIDADE", "k4", hash, 201, "nao-json"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
