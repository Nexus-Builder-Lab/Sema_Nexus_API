package br.com.nexusbuilderlab.semanexus.demo;

import br.com.nexusbuilderlab.semanexus.demo.DemoMassaDocumentos.DocSeed;
import br.com.nexusbuilderlab.semanexus.demo.DemoMassaGerador.DemandaSeed;
import br.com.nexusbuilderlab.semanexus.demo.DemoMassaGerador.MunicipioRef;
import br.com.nexusbuilderlab.semanexus.docia.documento.TextExtractor;
import br.com.nexusbuilderlab.semanexus.docia.documento.TipoArquivo;
import br.com.nexusbuilderlab.semanexus.docia.storage.StorageService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Massa demonstrativa de demandas, fiscalizações e documentos (Issue #18). Idempotente: as demandas levam a
 * marca {@value DemoMassaGerador#MARCA} em OBSERVACOES e os documentos são identificados por (título, arquivo).
 * Datas relativas ao dia da execução. Só nos profiles demo/dev. AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.
 */
@Service
@Profile(DemoSeedService.PROFILES)
public class DemoMassaService {

    static final ZoneId FUSO = ZoneId.of("America/Cuiaba");
    private static final String SQL_HIST = "INSERT INTO DEMANDA_HISTORICO (DEMANDA_ID, TIPO, STATUS_ANTERIOR, "
            + "STATUS_NOVO, TEXTO, USUARIO_ID, DATA) VALUES (?, ?, ?, ?, ?, ?, ?)";

    public record ResultadoMassa(int demandas, int documentos, int vinculos) {
        public boolean vazio() {
            return demandas == 0 && documentos == 0 && vinculos == 0;
        }
    }

    private final JdbcTemplate jdbc;
    private final StorageService storage;
    private final TextExtractor extrator;

    public DemoMassaService(JdbcTemplate jdbc, StorageService storage, TextExtractor extrator) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.extrator = extrator;
    }

    @Transactional
    public ResultadoMassa aplicar() {
        return aplicar(LocalDate.now(FUSO));
    }

    ResultadoMassa aplicar(LocalDate hoje) {
        long analista = usuario("analista@demo.semanexus.local");
        long gestor = usuario("gestor@demo.semanexus.local");

        int novasDemandas = 0;
        Integer existentes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM DEMANDA WHERE OBSERVACOES LIKE ?", Integer.class, DemoMassaGerador.MARCA + "%");
        if (existentes == null || existentes < DemoMassaGerador.TOTAL) {
            if (existentes != null && existentes > 0) {
                throw new IllegalStateException("Seed da massa: carga parcial detectada; execute o demo-reset.");
            }
            novasDemandas = inserirDemandas(hoje, analista, gestor);
        }
        int[] docs = inserirDocumentos(hoje, analista, gestor);
        return new ResultadoMassa(novasDemandas, docs[0], docs[1]);
    }

    // ------------------------------------------------------------------ demandas

    private int inserirDemandas(LocalDate hoje, long analista, long gestor) {
        String ids = DemoMassaGerador.MUNICIPIOS_IDS.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<MunicipioRef> municipios = jdbc.query("SELECT ID, NOME, BIOMA, LATITUDE, LONGITUDE, REGIAO FROM MUNICIPIO "
                        + "WHERE ID IN (" + ids + ") ORDER BY ID",
                (rs, n) -> new MunicipioRef(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDouble(4),
                        rs.getDouble(5), rs.getString(6)));
        List<DemandaSeed> lista = DemoMassaGerador.gerar(hoje, municipios);
        // ordem estável independente do banco: a posição na lista de ids fixa define o sorteio
        Map<Integer, Integer> sequencia = new java.util.HashMap<>();
        Map<String, Long[]> nucleos = new java.util.HashMap<>();

        for (DemandaSeed d : lista) {
            int ano = d.recebimento().getYear();
            int seq = sequencia.computeIfAbsent(ano, this::ultimoNumero) + 1;
            sequencia.put(ano, seq);
            String numero = "DEM-%d/%04d".formatted(ano, seq);
            Long[] nucleo = d.passou("DISTRIBUIDA") ? nucleo(nucleos, d.municipio().regiao()) : null;
            Long equipe = nucleo != null ? nucleo[0] : null;
            Long fiscal = nucleo != null ? nucleo[1] : null;
            LocalDateTime criada = d.recebimento().atTime(8, 0);
            LocalDateTime ultima = ultimaMovimentacao(d);

            jdbc.update("INSERT INTO DEMANDA (NUMERO, TITULO, DESCRICAO, ORIGEM, CATEGORIA, MUNICIPIO_ID, LOCALIDADE, "
                            + "LATITUDE, LONGITUDE, DATA_RECEBIMENTO, PRIORIDADE, PRAZO_SUGERIDO, PRAZO, STATUS, "
                            + "EQUIPE_ID, FISCAL_ID, OBSERVACOES, RESULTADO_FINAL, JUSTIFICATIVA_CONCLUSAO, "
                            + "CONCLUIDA_EM, CRIADO_POR, CRIADO_EM, ATUALIZADO_EM) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    numero, d.titulo(), d.descricao(), d.origem(), d.categoria(), d.municipio().id(), d.localidade(),
                    d.latitude(), d.longitude(), Date.valueOf(d.recebimento()), d.prioridade(),
                    data(d.prazoSugerido()), data(d.prazo()), d.status(), equipe, fiscal,
                    DemoMassaGerador.MARCA + " Registro fictício do seed demonstrativo.",
                    d.concluidaEm() == null ? null : "Demanda atendida e concluída (resultado fictício). "
                            + "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.",
                    d.concluidaEm() == null ? null : "Conclusão demonstrativa conforme relatório de fiscalização.",
                    d.concluidaEm() == null ? null : Timestamp.valueOf(d.concluidaEm().atTime(16, 0)),
                    analista, Timestamp.valueOf(criada), Timestamp.valueOf(ultima));
            long id = jdbc.queryForObject("SELECT ID FROM DEMANDA WHERE NUMERO = ?", Long.class, numero);

            List<Object[]> hist = new ArrayList<>();
            hist.add(h(id, "CADASTRO", null, "PENDENTE", "Demanda cadastrada (fictícia).", analista, criada));
            if (d.passou("EM_TRIAGEM")) {
                hist.add(h(id, "TRIAGEM", "PENDENTE", "EM_TRIAGEM", "Triagem iniciada.", analista,
                        d.recebimento().plusDays(1).atTime(9, 0)));
            }
            if (d.passou("AGUARDANDO_DISTRIBUICAO")) {
                hist.add(h(id, "TRIAGEM", "EM_TRIAGEM", "AGUARDANDO_DISTRIBUICAO",
                        "Triagem concluída: categoria, prioridade e prazo sugerido definidos.", gestor,
                        d.recebimento().plusDays(1).atTime(14, 0)));
            }
            if (d.passou("DISTRIBUIDA")) {
                hist.add(h(id, "DISTRIBUICAO", "AGUARDANDO_DISTRIBUICAO", "DISTRIBUIDA",
                        "Demanda distribuída à equipe e ao fiscal responsáveis.", gestor,
                        d.recebimento().plusDays(2).atTime(10, 0)));
                jdbc.update("INSERT INTO DISTRIBUICAO (DEMANDA_ID, EQUIPE_ID, FISCAL_ID, DATA, PRAZO, OBSERVACOES, "
                                + "USUARIO_ID) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        id, equipe, fiscal, Timestamp.valueOf(d.recebimento().plusDays(2).atTime(10, 0)),
                        Date.valueOf(d.prazo()), "Distribuição fictícia do seed demonstrativo.", gestor);
            }
            if (d.passou("EM_ANDAMENTO")) {
                hist.add(h(id, "INICIO", "DISTRIBUIDA", "EM_ANDAMENTO", "Atendimento iniciado pelo fiscal.", fiscal,
                        d.recebimento().plusDays(3).atTime(8, 30)));
                hist.add(h(id, "ATIVIDADE", "EM_ANDAMENTO", "EM_ANDAMENTO",
                        "Atividade registrada: levantamento preliminar em campo (fictício).", fiscal,
                        d.recebimento().plusDays(5).atTime(11, 0)));
            }
            if (d.passou("AGUARDANDO_CONCLUSAO")) {
                hist.add(h(id, "FISCALIZACAO", "EM_ANDAMENTO", "AGUARDANDO_CONCLUSAO",
                        "Fiscalização registrada; aguardando conclusão do gestor.", fiscal,
                        d.fiscalizacaoData().atTime(15, 0)));
                jdbc.update("INSERT INTO FISCALIZACAO (DEMANDA_ID, DATA, EQUIPE_ID, ATIVIDADES, RESULTADO, "
                                + "EVIDENCIAS, RECOMENDACOES, REGISTRADO_POR, REGISTRADO_EM) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        id, Date.valueOf(d.fiscalizacaoData()), equipe,
                        "Vistoria em campo e conferência das coordenadas (fictício).",
                        "Irregularidade confirmada em " + d.municipio().nome() + " (resultado simulado).",
                        "Registros fotográficos e coordenadas simulados.",
                        "Notificar o responsável fictício e acompanhar a regularização.",
                        fiscal, Timestamp.valueOf(d.fiscalizacaoData().atTime(15, 0)));
            }
            if (d.passou("FINALIZADA")) {
                hist.add(h(id, "CONCLUSAO", "AGUARDANDO_CONCLUSAO", "FINALIZADA",
                        "Demanda concluída pelo gestor (fictício).", gestor, d.concluidaEm().atTime(16, 0)));
            }
            jdbc.batchUpdate(SQL_HIST, hist);
        }
        for (var e : sequencia.entrySet()) {
            jdbc.update("MERGE INTO NUMERACAO_DEMANDA n USING (SELECT ? ANO FROM DUAL) s ON (n.ANO = s.ANO) "
                    + "WHEN MATCHED THEN UPDATE SET n.ULTIMO = ? "
                    + "WHEN NOT MATCHED THEN INSERT (ANO, ULTIMO) VALUES (s.ANO, ?)", e.getKey(), e.getValue(),
                    e.getValue());
        }
        return lista.size();
    }

    /** Equipe e fiscal responsável do núcleo regional da região (cache por sigla do setor). */
    private Long[] nucleo(Map<String, Long[]> cache, String regiao) {
        return cache.computeIfAbsent(DemoMassaGerador.siglaNucleo(regiao), sigla -> {
            List<Long[]> r = jdbc.query("SELECT e.ID, e.RESPONSAVEL_ID FROM EQUIPE e JOIN SETOR s ON s.ID = e.SETOR_ID "
                    + "WHERE s.SIGLA = ? AND e.NOME LIKE 'Equipe % (demonstrativa)' AND e.RESPONSAVEL_ID IS NOT NULL",
                    (rs, n) -> new Long[] {rs.getLong(1), rs.getLong(2)}, sigla);
            if (r.size() != 1) {
                throw new IllegalStateException("Seed da massa: equipe do núcleo " + sigla
                        + " ausente (execute o seed base).");
            }
            return r.get(0);
        });
    }

    private static LocalDateTime ultimaMovimentacao(DemandaSeed d) {
        if (d.concluidaEm() != null) {
            return d.concluidaEm().atTime(16, 0);
        }
        if (d.fiscalizacaoData() != null) {
            return d.fiscalizacaoData().atTime(15, 0);
        }
        int pos = DemoMassaGerador.STATUS_ORDEM.indexOf(d.status());
        int dias = pos == 0 ? 0 : pos <= 2 ? 1 : pos == 3 ? 2 : 5;
        return d.recebimento().plusDays(dias).atTime(14, 0);
    }

    private static Object[] h(long demanda, String tipo, String anterior, String novo, String texto, long usuario,
                              LocalDateTime data) {
        return new Object[] {demanda, tipo, anterior, novo, texto, usuario, Timestamp.valueOf(data)};
    }

    private static Date data(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    private int ultimoNumero(int ano) {
        List<Integer> r = jdbc.queryForList("SELECT ULTIMO FROM NUMERACAO_DEMANDA WHERE ANO = ?", Integer.class, ano);
        return r.isEmpty() ? 0 : r.get(0);
    }

    private long usuario(String email) {
        List<Long> r = jdbc.queryForList("SELECT ID FROM USUARIO WHERE EMAIL = ?", Long.class, email);
        if (r.isEmpty()) {
            throw new IllegalStateException("Seed da massa: usuário demonstrativo ausente: " + email);
        }
        return r.get(0);
    }

    // ------------------------------------------------------------------ documentos

    private int[] inserirDocumentos(LocalDate hoje, long analista, long gestor) {
        int novos = 0;
        int vinculos = 0;
        List<String> gravados = new ArrayList<>();
        try {
            for (DocSeed s : DemoMassaDocumentos.catalogo()) {
                Long docId = idDocumento(s);
                if (docId == null) {
                    byte[] bytes = s.conteudo();
                    String chave = storage.store(bytes);
                    gravados.add(chave);
                    TipoArquivo tipo = TipoArquivo.porNome(s.nomeArquivo()).orElseThrow();
                    LocalDate dataDoc = hoje.minusDays(s.idadeDias());
                    jdbc.update("INSERT INTO DOCUMENTO (TITULO, DESCRICAO, CATEGORIA, TIPO, DATA_DOCUMENTO, "
                                    + "MUNICIPIO_ID, PALAVRAS_CHAVE, NOME_ARQUIVO, MIME, TAMANHO, STORAGE_KEY, SHA256, "
                                    + "TEXTO_EXTRAIDO, RESPONSAVEL_ID, CRIADO_EM) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            s.titulo(), s.descricao(), s.categoria(), s.tipo(), Date.valueOf(dataDoc),
                            s.municipioId(), s.palavrasChave(), s.nomeArquivo(), tipo.mime(), bytes.length, chave,
                            sha256(bytes), extrator.extrair(tipo, bytes), analista,
                            Timestamp.valueOf(dataDoc.atTime(10, 0)));
                    docId = idDocumento(s);
                    novos++;
                }
                List<Long> demanda = jdbc.queryForList("SELECT ID FROM DEMANDA WHERE TITULO = ? AND OBSERVACOES LIKE ?",
                        Long.class, tituloCenario(s.demandaChave()), DemoMassaGerador.MARCA + "%");
                if (!demanda.isEmpty() && vinculado(docId, demanda.get(0)) == 0) {
                    LocalDateTime quando = hoje.minusDays(s.idadeDias()).atTime(11, 0);
                    jdbc.update("INSERT INTO DOCUMENTO_DEMANDA (DOCUMENTO_ID, DEMANDA_ID, VINCULADO_EM, "
                            + "VINCULADO_POR) VALUES (?, ?, ?, ?)", docId, demanda.get(0), Timestamp.valueOf(quando),
                            gestor);
                    jdbc.update(SQL_HIST, demanda.get(0), "DOCUMENTO", null, null,
                            "Documento vinculado: " + s.titulo(), gestor, Timestamp.valueOf(quando));
                    vinculos++;
                }
            }
        } catch (IOException e) {
            removerSilencioso(gravados);
            throw new UncheckedIOException("Seed da massa: falha ao gravar documento no armazenamento.", e);
        } catch (RuntimeException e) {
            removerSilencioso(gravados);
            throw e;
        }
        return new int[] {novos, vinculos};
    }

    private static String tituloCenario(String chave) {
        return DemoMassaGerador.CENARIOS.stream().filter(c -> c.chave().equals(chave)).findFirst().orElseThrow()
                .titulo();
    }

    private Long idDocumento(DocSeed s) {
        List<Long> r = jdbc.queryForList("SELECT ID FROM DOCUMENTO WHERE TITULO = ? AND NOME_ARQUIVO = ?", Long.class,
                s.titulo(), s.nomeArquivo());
        return r.isEmpty() ? null : r.get(0);
    }

    private int vinculado(long documento, long demanda) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM DOCUMENTO_DEMANDA WHERE DOCUMENTO_ID = ? AND DEMANDA_ID = ?", Integer.class,
                documento, demanda);
        return n == null ? 0 : n;
    }

    private void removerSilencioso(List<String> chaves) {
        for (String c : chaves) {
            try {
                storage.delete(c);
            } catch (IOException | RuntimeException ignorado) {
                // melhor esforço
            }
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
