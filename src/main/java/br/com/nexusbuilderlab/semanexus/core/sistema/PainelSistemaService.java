package br.com.nexusbuilderlab.semanexus.core.sistema;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import br.com.nexusbuilderlab.semanexus.agentes.AgentesConfiguracao;
import br.com.nexusbuilderlab.semanexus.agentes.AgentesController;
import br.com.nexusbuilderlab.semanexus.agentes.ExecutorAgente;
import br.com.nexusbuilderlab.semanexus.agentes.MetricasAgentes;
import br.com.nexusbuilderlab.semanexus.core.sistema.PainelSistema.AgentePainel;
import br.com.nexusbuilderlab.semanexus.core.sistema.PainelSistema.Estado;
import br.com.nexusbuilderlab.semanexus.core.sistema.PainelSistema.Sistema;
import br.com.nexusbuilderlab.semanexus.core.sistema.PainelSistema.Verificacao;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaDto;
import br.com.nexusbuilderlab.semanexus.intelligence.referencia.ReferenciaSateliteService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Monta o Painel do sistema (Issue #114). Cada verificação é isolada: falha de um componente vira INDISPONIVEL sem
 * derrubar o painel. Só leitura; nada sensível (chaves aparecem só como configuradas ou não).
 */
@Service
public class PainelSistemaService {

    static final long LIMITE_ARQUIVOS = 50_000;
    static final long ESPACO_MINIMO_MB = 1024;

    private final JdbcTemplate jdbc;
    private final ExecutorAgente executor;
    private final AgentesConfiguracao agentes;
    private final MetricasAgentes metricas;
    private final ReferenciaSateliteService referencia;
    private final Environment ambiente;
    private final Path armazenamento;

    public PainelSistemaService(JdbcTemplate jdbc, ExecutorAgente executor, AgentesConfiguracao agentes,
                                MetricasAgentes metricas, ReferenciaSateliteService referencia, Environment ambiente,
                                @Value("${semanexus.storage.dir:/data/uploads}") String armazenamento) {
        this.jdbc = jdbc;
        this.executor = executor;
        this.agentes = agentes;
        this.metricas = metricas;
        this.referencia = referencia;
        this.ambiente = ambiente;
        this.armazenamento = Path.of(armazenamento);
    }

    public PainelSistema montar() {
        List<Verificacao> verificacoes = List.of(
                verificar("api", "API (Spring Boot)", this::api),
                verificar("banco", "Banco de dados (Oracle)", this::banco),
                verificar("ia", "IA dos agentes", this::ia),
                verificar("inpe", "Dados públicos do INPE (focos de calor e DETER)", this::inpe),
                verificar("armazenamento", "Armazenamento de documentos", this::armazenamento));
        return new PainelSistema(LocalDateTime.now(), sistema(), verificacoes, agentesDoPainel());
    }

    /** Resultado parcial de uma verificação (estado, resumo e detalhes). */
    record Parcial(Estado estado, String resumo, Map<String, Object> detalhes) {
    }

    private Verificacao verificar(String id, String nome, Supplier<Parcial> verificacao) {
        long t0 = System.currentTimeMillis();
        try {
            Parcial p = verificacao.get();
            return new Verificacao(id, nome, p.estado(), System.currentTimeMillis() - t0, p.resumo(), p.detalhes());
        } catch (RuntimeException e) {
            return new Verificacao(id, nome, Estado.INDISPONIVEL, System.currentTimeMillis() - t0,
                    "Falha na verificação (" + e.getClass().getSimpleName() + ").", Map.of());
        }
    }

    private Sistema sistema() {
        Runtime rt = Runtime.getRuntime();
        long inicio = ManagementFactory.getRuntimeMXBean().getStartTime();
        String versao = PainelSistemaService.class.getPackage().getImplementationVersion();
        return new Sistema(versao == null ? "desenvolvimento" : versao,
                String.join(",", ambiente.getActiveProfiles()), System.getProperty("java.version"),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(inicio), ZoneId.systemDefault()),
                (System.currentTimeMillis() - inicio) / 1000,
                (rt.totalMemory() - rt.freeMemory()) / 1_048_576, rt.maxMemory() / 1_048_576);
    }

    Parcial api() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("perfil", String.join(",", ambiente.getActiveProfiles()));
        d.put("java", System.getProperty("java.version"));
        d.put("documentacao", "/swagger-ui.html");
        return new Parcial(Estado.OK, "Respondendo.", d);
    }

    Parcial banco() {
        jdbc.queryForObject("SELECT 1 FROM DUAL", Integer.class);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("versao", jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductVersion()
                .lines().findFirst().orElse("")));
        d.put("demandas", jdbc.queryForObject("SELECT COUNT(*) FROM DEMANDA WHERE EXCLUIDO_EM IS NULL", Long.class));
        d.put("documentos", jdbc.queryForObject("SELECT COUNT(*) FROM DOCUMENTO WHERE EXCLUIDO_EM IS NULL", Long.class));
        d.put("usuariosAtivos", jdbc.queryForObject("SELECT COUNT(*) FROM USUARIO WHERE ATIVO = 1", Long.class));
        return new Parcial(Estado.OK, "Conectado.", d);
    }

    Parcial ia() {
        Map<String, Object> d = new LinkedHashMap<>(executor.diagnosticoProvedor());
        d.put("provedorConfigurado", agentes.provedor().name());
        d.put("modo", modo());
        if (agentes.provedor() == AgentesConfiguracao.Provedor.DEMO) {
            return new Parcial(Estado.ATENCAO, "Configurada só com regras locais (sem IA).", d);
        }
        if (!executor.iaDisponivel()) {
            return new Parcial(Estado.INDISPONIVEL,
                    "IA indisponível: os agentes usam as regras locais (modo demonstrativo).", d);
        }
        return new Parcial(Estado.OK, agentes.provedor() == AgentesConfiguracao.Provedor.ANTHROPIC
                ? "Claude disponível." : "Modelo local disponível (sem custo; os dados não saem do servidor).", d);
    }

    Parcial inpe() {
        ReferenciaDto.Metadados m = referencia.metadados();
        List<Map<String, Object>> importacoes = new ArrayList<>();
        long total = 0;
        for (ReferenciaDto.Importacao i : m.importacoes()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("tipo", i.tipo());
            x.put("fonte", i.fonte());
            x.put("periodoDe", i.periodoDe());
            x.put("periodoAte", i.periodoAte());
            x.put("registros", i.registros());
            x.put("importadoEm", i.importadoEm());
            x.put("licenca", i.licenca());
            x.put("origem", i.urlOrigem());
            importacoes.add(x);
            total += i.registros();
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("fonte", m.fonte());
        d.put("oficialSema", m.oficialSema());
        d.put("atualizadoEm", m.atualizadoEm());
        d.put("totalRegistros", total);
        d.put("importacoes", importacoes);
        return importacoes.isEmpty()
                ? new Parcial(Estado.ATENCAO, "Nenhum pacote do INPE importado.", d)
                : new Parcial(Estado.OK, total + " registros públicos em " + importacoes.size() + " pacote(s).", d);
    }

    Parcial armazenamento() {
        if (!Files.isDirectory(armazenamento)) {
            return new Parcial(Estado.INDISPONIVEL, "Diretório de arquivos não encontrado.", Map.of());
        }
        try {
            FileStore fs = Files.getFileStore(armazenamento);
            long livreMb = fs.getUsableSpace() / 1_048_576;
            long totalMb = fs.getTotalSpace() / 1_048_576;
            long arquivos;
            long bytes;
            try (Stream<Path> s = Files.walk(armazenamento)) {
                List<Path> lista = s.filter(Files::isRegularFile).limit(LIMITE_ARQUIVOS).toList();
                arquivos = lista.size();
                bytes = lista.stream().mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0;
                    }
                }).sum();
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("arquivos", arquivos);
            d.put("usadoMb", bytes / 1_048_576);
            d.put("livreMb", livreMb);
            d.put("totalMb", totalMb);
            return new Parcial(livreMb < ESPACO_MINIMO_MB ? Estado.ATENCAO : Estado.OK,
                    arquivos + " arquivo(s); " + livreMb / 1024 + " GB livres.", d);
        } catch (IOException e) {
            return new Parcial(Estado.INDISPONIVEL, "Não foi possível ler o volume de arquivos.", Map.of());
        }
    }

    private String modo() {
        if (!executor.iaDisponivel()) {
            return AgentesController.MODO_DEMONSTRATIVO;
        }
        return agentes.provedor() == AgentesConfiguracao.Provedor.ANTHROPIC
                ? AgentesController.MODO_CLAUDE : AgentesController.MODO_LOCAL;
    }

    private List<AgentePainel> agentesDoPainel() {
        String modo = modo();
        boolean ia = !AgentesController.MODO_DEMONSTRATIVO.equals(modo);
        return List.of(
                new AgentePainel("triagem", "Agente de Triagem", "SEMA Flux", modo,
                        ia ? executor.modelo(agentes.modeloTriagem()) : "Regras locais (sem IA generativa)",
                        List.of("obter_demanda", "buscar_demandas_semelhantes", "alertas_inpe_municipio"),
                        metricas.resumo("triagem")),
                new AgentePainel("docia", "Assistente DocIA", "SEMA DocIA", modo,
                        ia ? executor.modelo(agentes.modeloDocia()) : "Busca extrativa (sem IA generativa)",
                        List.of("buscar_documentos", "ler_documento"), metricas.resumo("docia")),
                new AgentePainel("minuta", "Agente Copiloto de Minuta", "SEMA Flux", modo,
                        ia ? executor.modelo(agentes.modeloDocia()) : "Template determinístico (sem IA generativa)",
                        List.of("obter_dossie_demanda", "ler_registro_fiscalizacao", "resumo_documentos_vinculados",
                                "alertas_inpe_area"),
                        metricas.resumo("minuta")));
    }
}
