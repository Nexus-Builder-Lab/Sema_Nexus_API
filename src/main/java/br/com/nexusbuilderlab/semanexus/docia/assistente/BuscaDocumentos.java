package br.com.nexusbuilderlab.semanexus.docia.assistente;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import br.com.nexusbuilderlab.semanexus.docia.documento.Documento;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoSpecification;
import br.com.nexusbuilderlab.semanexus.docia.vetorial.BuscaVetorial;
import br.com.nexusbuilderlab.semanexus.docia.vetorial.EmbeddingsConfiguracao;
import br.com.nexusbuilderlab.semanexus.docia.vetorial.Recuperacao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Busca textual da biblioteca (mesma da listagem, sem acento/caixa): cada termo da consulta pontua os documentos
 * ativos; devolve os melhores com o trecho mais aderente. Usada pelo modo extrativo e pelo Agente DocIA (#108).
 * Desde a Issue #120 cada termo longo vira radical ("desmatada" encontra "desmatamento") e a ocorrência no título
 * vale 2 pontos a mais.
 * <p>
 * Desde a Issue #141 a busca é <b>híbrida</b> quando há embeddings: o ranking lexical acima e o ranking vetorial
 * ({@link BuscaVetorial}, cosseno no Oracle 23ai) são fundidos por RRF (Reciprocal Rank Fusion):
 * {@code pontuação(d) = Σ 1 / (k + posição de d em cada ranking)}, {@code k = BUSCA_RRF_K} (padrão 60). Documento
 * que só a parte vetorial encontrou entra apenas com similaridade ≥ {@code EMBEDDINGS_SIMILARIDADE_MINIMA}, para a
 * busca continuar se abstendo em perguntas fora do acervo. Sem embeddings (desligados, modelo ausente, Ollama fora
 * do ar, nada indexado) o resultado é o lexical de antes, marcado {@link Recuperacao#LEXICAL}.
 */
@Component
public class BuscaDocumentos {

    /** Candidatos pedidos à parte vetorial antes da fusão. */
    static final int CANDIDATOS_VETORIAIS = 20;

    private final JdbcTemplate jdbc;
    private final DocumentoRepository documentos;
    private final BuscaVetorial vetorial;
    private final int rrfK;
    private final double similaridadeMinima;

    /** Só lexical (testes e configuração sem embeddings). */
    public BuscaDocumentos(JdbcTemplate jdbc, DocumentoRepository documentos) {
        this(jdbc, documentos, null, EmbeddingsConfiguracao.RRF_K_PADRAO,
                EmbeddingsConfiguracao.SIMILARIDADE_MINIMA_PADRAO);
    }

    @Autowired
    public BuscaDocumentos(JdbcTemplate jdbc, DocumentoRepository documentos, BuscaVetorial vetorial,
                           EmbeddingsConfiguracao configuracao) {
        this(jdbc, documentos, vetorial, configuracao.rrfK(), configuracao.similaridadeMinima());
    }

    public BuscaDocumentos(JdbcTemplate jdbc, DocumentoRepository documentos, BuscaVetorial vetorial, int rrfK,
                           double similaridadeMinima) {
        this.jdbc = jdbc;
        this.documentos = documentos;
        this.vetorial = vetorial;
        this.rrfK = rrfK;
        this.similaridadeMinima = similaridadeMinima;
    }

    /**
     * Documento encontrado, o trecho (frase) mais aderente, a aderência textual (alta/média/baixa; nula quando o
     * documento não tem nenhum termo da consulta e veio só da parte vetorial) e a similaridade semântica (cosseno do
     * trecho mais próximo; nula quando a parte vetorial não rodou ou não trouxe o documento).
     */
    public record Achado(Documento documento, String trecho, String aderencia, Double similaridadeSemantica) {

        public Achado(Documento documento, String trecho) {
            this(documento, trecho, "alta", null);
        }

        public Achado(Documento documento, String trecho, String aderencia) {
            this(documento, trecho, aderencia, null);
        }
    }

    /** Resultado da busca com o modo de recuperação usado. */
    public record Resultado(List<Achado> achados, Recuperacao recuperacao) {

        public static Resultado lexical(List<Achado> achados) {
            return new Resultado(achados, Recuperacao.LEXICAL);
        }
    }

    /**
     * Aderência textual <b>relativa</b>: pontos do documento (termos da consulta encontrados; título vale mais) divididos
     * pelos pontos do documento mais aderente da mesma busca. Alta: 70%+ do melhor; média: 40–70%; baixa: o restante.
     * Não é confiança factual nem probabilidade de a resposta estar correta: o melhor documento de uma busca fraca
     * também sai "alta". Por isso o rótulo se chama aderência textual (relatório técnico, P2).
     * <p>
     * Documentos com menos de 1/3 da pontuação do melhor ficam de fora do ranking lexical: modelos pequenos escolhem
     * pelo trecho, e um documento pouco relevante com números no trecho os desviava (ex.: Parecer de Cáceres numa
     * pergunta sobre Colniza).
     */
    static String aderenciaTextual(int pontos, int maximo) {
        double p = maximo == 0 ? 0 : (double) pontos / maximo;
        return p >= 0.7 ? "alta" : p >= 0.4 ? "média" : "baixa";
    }

    /** Radical simples para a busca: palavras com 7+ letras perdem o final (no mínimo 5 letras ficam). */
    static String radical(String termo) {
        return termo.length() >= 7 ? termo.substring(0, Math.max(5, termo.length() - 3)) : termo;
    }

    /** Termos normalizados da consulta já reduzidos ao radical. */
    public static List<String> radicais(String texto) {
        List<String> r = new ArrayList<>();
        for (String t : AssistenteService.termos(DocumentoSpecification.normalizar(texto == null ? "" : texto.strip()))) {
            String rad = radical(t);
            if (!r.contains(rad)) {
                r.add(rad);
            }
        }
        return r;
    }

    public List<Achado> buscar(String consulta, int limite) {
        return pesquisar(consulta, limite, null).achados();
    }

    /** {@code idsPermitidos} não nulo restringe o resultado a esses documentos (ex.: vinculados à demanda). */
    public List<Achado> buscar(String consulta, int limite, Set<Long> idsPermitidos) {
        return pesquisar(consulta, limite, idsPermitidos).achados();
    }

    /** Busca híbrida quando possível; lexical caso contrário. */
    public Resultado pesquisar(String consulta, int limite, Set<Long> idsPermitidos) {
        return pesquisar(consulta, limite, idsPermitidos, true);
    }

    /**
     * @param permitirVetorial {@code false} força a busca só lexical (comparação do benchmark, {@code modo=LEXICAL})
     */
    public Resultado pesquisar(String consulta, int limite, Set<Long> idsPermitidos, boolean permitirVetorial) {
        return pesquisar(preparar(consulta, permitirVetorial), limite, idsPermitidos);
    }

    /**
     * Consulta com o embedding já calculado (ou nulo: só lexical). Separar as etapas permite chamar o modelo fora de
     * transação e só depois abrir a consulta ao banco.
     */
    public record ConsultaPreparada(String texto, float[] vetor) {
    }

    /** Etapa de rede: gera o embedding da consulta quando a parte vetorial pode rodar. Nunca lança. */
    public ConsultaPreparada preparar(String consulta, boolean permitirVetorial) {
        float[] v = null;
        if (permitirVetorial && vetorial != null && consulta != null && !consulta.isBlank()) {
            v = vetorial.vetorizar(consulta).orElse(null);
        }
        return new ConsultaPreparada(consulta, v);
    }

    /** Etapa de banco: ranking lexical, ranking vetorial (se houver embedding) e fusão RRF. */
    public Resultado pesquisar(ConsultaPreparada preparada, int limite, Set<Long> idsPermitidos) {
        String consulta = preparada.texto();
        List<String> termos = radicais(consulta);
        Map<Long, Integer> pontos = new LinkedHashMap<>();
        for (String t : termos) {
            String padrao = DocumentoSpecification.padraoBusca(t);
            for (Long id : jdbc.queryForList(DocumentoSpecification.SQL_BUSCA, Long.class,
                    padrao, padrao, padrao, padrao)) {
                pontos.merge(id, 1, Integer::sum);
            }
            for (Long id : jdbc.queryForList(DocumentoSpecification.SQL_BUSCA_TITULO, Long.class, padrao)) {
                pontos.merge(id, 2, Integer::sum);
            }
        }
        if (idsPermitidos != null) {
            pontos.keySet().retainAll(idsPermitidos);
        }
        int maximo = pontos.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Long> lexical = pontos.entrySet().stream()
                .filter(e -> e.getValue() * 3 >= maximo)
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();

        Optional<List<BuscaVetorial.AchadoVetorial>> vet = preparada.vetor() == null || vetorial == null
                ? Optional.empty() : vetorial.buscar(preparada.vetor(), CANDIDATOS_VETORIAIS, idsPermitidos);
        if (vet == null || vet.isEmpty()) {
            List<Achado> r = new ArrayList<>();
            for (Long id : lexical) {
                if (r.size() >= limite) {
                    break;
                }
                documentos.buscarAtivo(id).ifPresent(d -> {
                    String trecho = AssistenteService.melhorTrecho(d, termos);
                    if (trecho != null) {
                        r.add(new Achado(d, trecho, aderenciaTextual(pontos.get(id), maximo)));
                    }
                });
            }
            return Resultado.lexical(r);
        }

        Map<Long, BuscaVetorial.AchadoVetorial> porDocumento = new LinkedHashMap<>();
        for (BuscaVetorial.AchadoVetorial a : vet.get()) {
            if (idsPermitidos == null || idsPermitidos.contains(a.documentoId())) {
                porDocumento.putIfAbsent(a.documentoId(), a);
            }
        }
        // Ranking vetorial só acima do limiar (senão toda pergunta "encontraria" o vizinho mais próximo).
        List<Long> semantico = porDocumento.values().stream()
                .filter(a -> a.similaridade() >= similaridadeMinima)
                .sorted(Comparator.comparingDouble(BuscaVetorial.AchadoVetorial::similaridade).reversed()
                        .thenComparingLong(BuscaVetorial.AchadoVetorial::documentoId))
                .map(BuscaVetorial.AchadoVetorial::documentoId)
                .toList();
        List<Achado> r = new ArrayList<>();
        for (Long id : fundirRrf(lexical, semantico, rrfK)) {
            if (r.size() >= limite) {
                break;
            }
            BuscaVetorial.AchadoVetorial v = porDocumento.get(id);
            documentos.buscarAtivo(id).ifPresent(d -> {
                String trecho = termos.isEmpty() ? null : AssistenteService.melhorTrecho(d, termos);
                if (trecho == null && v != null && v.trecho() != null && !v.trecho().isBlank()) {
                    trecho = v.trecho().strip();
                    if (trecho.length() > AssistenteService.MAX_TRECHO) {
                        trecho = trecho.substring(0, AssistenteService.MAX_TRECHO).strip() + "…";
                    }
                }
                if (trecho != null) {
                    Integer p = pontos.get(id);
                    r.add(new Achado(d, trecho, p == null ? null : aderenciaTextual(p, maximo),
                            v == null ? null : arredondar(v.similaridade())));
                }
            });
        }
        return new Resultado(r, Recuperacao.HIBRIDA);
    }

    /**
     * Reciprocal Rank Fusion: soma {@code 1/(k + posição)} (posição a partir de 1) de cada ranking; empate pelo id.
     * Usa só as posições, porque as pontuações brutas têm escalas diferentes (pontos de termos × cosseno).
     */
    static List<Long> fundirRrf(List<Long> lexical, List<Long> semantico, int k) {
        Map<Long, Double> soma = new LinkedHashMap<>();
        for (int i = 0; i < lexical.size(); i++) {
            soma.merge(lexical.get(i), 1.0 / (k + i + 1), Double::sum);
        }
        for (int i = 0; i < semantico.size(); i++) {
            soma.merge(semantico.get(i), 1.0 / (k + i + 1), Double::sum);
        }
        return soma.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static double arredondar(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
