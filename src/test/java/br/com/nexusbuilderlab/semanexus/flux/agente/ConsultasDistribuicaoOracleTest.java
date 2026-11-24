package br.com.nexusbuilderlab.semanexus.flux.agente;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.nexusbuilderlab.semanexus.core.catalogo.CategoriaFiscalizacao;
import br.com.nexusbuilderlab.semanexus.core.catalogo.Origem;
import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.equipe.Equipe;
import br.com.nexusbuilderlab.semanexus.core.equipe.EquipeRepository;
import br.com.nexusbuilderlab.semanexus.core.municipio.MunicipioRepository;
import br.com.nexusbuilderlab.semanexus.core.setor.Setor;
import br.com.nexusbuilderlab.semanexus.core.setor.SetorRepository;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.core.usuario.UsuarioRepository;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.Candidatas;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.Cargas;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.DemandaAtiva;
import br.com.nexusbuilderlab.semanexus.flux.agente.ConsultasDistribuicao.TempoMedio;
import br.com.nexusbuilderlab.semanexus.flux.demanda.Demanda;
import br.com.nexusbuilderlab.semanexus.flux.demanda.DemandaRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Consultas JPQL do Agente de Distribuição (Issue #130) contra Oracle real (Testcontainers): equipes candidatas e
 * ampliação, cargas agregadas, vistorias na região (ordem e limite no banco) e tempo médio. Fora do build padrão:
 * {@code mvn -B -ntp verify -Poracle}. Exige Docker.
 */
@Tag("oracle")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ConsultasDistribuicaoOracleTest {

    @Container
    @ServiceConnection
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart");

    static final int CUIABA = 5103403;
    static final int SINOP = 5107909;
    private static final AtomicInteger SEQ = new AtomicInteger(9000);

    @Autowired
    ConsultasDistribuicao consultas;
    @Autowired
    SetorRepository setores;
    @Autowired
    EquipeRepository equipes;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    DemandaRepository demandas;
    @Autowired
    MunicipioRepository municipios;

    private Usuario usuario(String nome, Perfil perfil) {
        return usuarios.save(new Usuario(nome, "u." + UUID.randomUUID() + "@exemplo.test", "hash-ficticio", perfil));
    }

    private Demanda demanda(Usuario autor, int municipio, StatusDemanda status, Equipe equipe, Usuario fiscal,
                            LocalDate prazo, CategoriaFiscalizacao cat, LocalDate recebida, LocalDate concluida) {
        Demanda d = new Demanda("DEM-2026/" + SEQ.incrementAndGet(), autor);
        d.setTitulo("Demanda fictícia " + d.getNumero());
        d.setDescricao("Descrição fictícia");
        d.setOrigem(Origem.DENUNCIA);
        d.setCategoria(cat);
        d.setMunicipio(municipios.findById(municipio).orElseThrow());
        d.setDataRecebimento(recebida);
        d.setStatus(status);
        d.setEquipe(equipe);
        d.setFiscal(fiscal);
        d.setPrazo(prazo);
        if (concluida != null) {
            d.setConcluidaEm(concluida.atTime(10, 0));
        }
        return demandas.save(d);
    }

    @Test
    void consultasAgregamOrdenamELimitamNoBanco() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Cuiaba"));
        Setor sno = setores.save(new Setor("NR-SNO", "Núcleo Regional de Sinop (teste)"));
        Setor cba = setores.save(new Setor("NR-CBA", "Núcleo Regional de Cuiabá (teste)"));
        Usuario autor = usuario("Autor", Perfil.GESTOR);
        Usuario ana = usuario("Ana Fiscal", Perfil.FISCAL);
        Usuario bia = usuario("Bia Fiscal", Perfil.FISCAL);
        Usuario inativo = usuario("Inativo Fiscal", Perfil.FISCAL);
        inativo.setAtivo(false);
        usuarios.save(inativo);

        Equipe a = new Equipe("Equipe A Sinop", sno);
        a.getMembros().add(ana);
        a.getMembros().add(inativo);
        a = equipes.save(a);
        Equipe b = new Equipe("Equipe B Cuiabá", cba);
        b.getMembros().add(bia);
        b = equipes.save(b);
        Equipe vazia = new Equipe("Equipe Vazia Sinop", sno);
        vazia.getMembros().add(inativo);
        equipes.save(vazia);

        // Cargas: Ana com 1 atrasada, 1 vencendo em 3 dias e 1 sem prazo; uma finalizada não conta.
        demanda(autor, SINOP, StatusDemanda.EM_ANDAMENTO, a, ana, hoje.minusDays(2), null, hoje.minusDays(20), null);
        demanda(autor, SINOP, StatusDemanda.DISTRIBUIDA, a, ana, hoje.plusDays(3), null, hoje.minusDays(5), null);
        demanda(autor, CUIABA, StatusDemanda.AGUARDANDO_CONCLUSAO, a, ana, null, null, hoje.minusDays(9), null);
        demanda(autor, SINOP, StatusDemanda.FINALIZADA, a, ana, hoje.minusDays(30), CategoriaFiscalizacao.PESCA_IRREGULAR,
                hoje.minusDays(40), hoje.minusDays(30));
        demanda(autor, SINOP, StatusDemanda.FINALIZADA, b, bia, hoje.minusDays(30), CategoriaFiscalizacao.PESCA_IRREGULAR,
                hoje.minusDays(25), hoje.minusDays(20));
        Demanda atual = demanda(autor, SINOP, StatusDemanda.AGUARDANDO_DISTRIBUICAO, null, null, null,
                CategoriaFiscalizacao.PESCA_IRREGULAR, hoje, null);

        Candidatas doSno = consultas.equipesCandidatas("NR-SNO");
        assertThat(doSno.ampliada()).isFalse();
        assertThat(doSno.equipes()).singleElement().satisfies(e -> {
            assertThat(e.nome()).isEqualTo("Equipe A Sinop");
            assertThat(e.membros()).extracting(m -> m.nome()).containsExactly("Ana Fiscal");
        });
        Candidatas semNucleo = consultas.equipesCandidatas("NR-BAG");
        assertThat(semNucleo.ampliada()).isTrue();
        assertThat(semNucleo.equipes()).extracting(e -> e.nome()).containsExactly("Equipe A Sinop", "Equipe B Cuiabá");

        Cargas cargas = consultas.cargas(List.of(a.getId(), b.getId()), hoje);
        assertThat(cargas.fiscal(ana.getId())).isEqualTo(new ConsultasDistribuicao.Carga(3, 1, 1));
        assertThat(cargas.equipe(a.getId()).ativas()).isEqualTo(3);
        assertThat(cargas.equipe(b.getId()).ativas()).isZero();

        List<DemandaAtiva> municipio = consultas.ativasNaRegiao(atual.getId(), SINOP, "Sinop", true);
        assertThat(municipio).hasSize(2).allMatch(DemandaAtiva::mesmoMunicipio);
        assertThat(municipio.get(0).prazo()).isEqualTo(hoje.minusDays(2));
        assertThat(municipio.get(0).equipe()).isEqualTo("Equipe A Sinop");
        assertThat(municipio.get(0).fiscal()).isEqualTo("Ana Fiscal");

        for (int i = 0; i < ConsultasDistribuicao.MAX_ATIVAS_REGIAO + 2; i++) {
            demanda(autor, SINOP, StatusDemanda.DISTRIBUIDA, b, bia, hoje.plusDays(10 + i), null, hoje, null);
        }
        assertThat(consultas.ativasNaRegiao(atual.getId(), SINOP, "Sinop", false))
                .hasSize(ConsultasDistribuicao.MAX_ATIVAS_REGIAO)
                .noneMatch(x -> x.numero().equals(atual.getNumero()));

        TempoMedio pesca = consultas.tempoMedio(CategoriaFiscalizacao.PESCA_IRREGULAR);
        assertThat(pesca.amostra()).isEqualTo(2);
        assertThat(pesca.mediaDias()).isEqualTo(7.5);
        assertThat(consultas.tempoMedio(CategoriaFiscalizacao.FAUNA_SILVESTRE).mediaDias()).isNull();
    }
}
