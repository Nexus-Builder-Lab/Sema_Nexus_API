package br.com.nexusbuilderlab.semanexus.docia.assistente;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.nexusbuilderlab.semanexus.docia.documento.Documento;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoSpecification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Busca textual da biblioteca (mesma da listagem, sem acento/caixa): cada termo da consulta pontua os documentos
 * ativos; devolve os melhores com o trecho mais aderente. Usada pelo modo extrativo e pelo Agente DocIA (#108).
 * Desde a Issue #120 cada termo longo vira radical ("desmatada" encontra "desmatamento") e a ocorrência no título
 * vale 2 pontos a mais.
 */
@Component
public class BuscaDocumentos {

    private final JdbcTemplate jdbc;
    private final DocumentoRepository documentos;

    public BuscaDocumentos(JdbcTemplate jdbc, DocumentoRepository documentos) {
        this.jdbc = jdbc;
        this.documentos = documentos;
    }

    /**
     * Documento encontrado, o trecho (frase) com mais termos da consulta e a aderência textual (alta/média/baixa).
     */
    public record Achado(Documento documento, String trecho, String aderencia) {

        public Achado(Documento documento, String trecho) {
            this(documento, trecho, "alta");
        }
    }

    /**
     * Aderência textual <b>relativa</b>: pontos do documento (termos da consulta encontrados; título vale mais) divididos
     * pelos pontos do documento mais aderente da mesma busca. Alta: 70%+ do melhor; média: 40–70%; baixa: o restante.
     * Não é confiança factual nem probabilidade de a resposta estar correta: o melhor documento de uma busca fraca
     * também sai "alta". Por isso o rótulo se chama aderência textual (relatório técnico, P2).
     * <p>
     * Documentos com menos de 1/3 da pontuação do melhor ficam de fora: modelos pequenos escolhem pelo trecho, e um
     * documento pouco relevante com números no trecho os desviava (ex.: Parecer de Cáceres numa pergunta sobre
     * Colniza).
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
        return buscar(consulta, limite, null);
    }

    /** {@code idsPermitidos} não nulo restringe o resultado a esses documentos (ex.: vinculados à demanda). */
    public List<Achado> buscar(String consulta, int limite, java.util.Set<Long> idsPermitidos) {
        List<String> termos = radicais(consulta);
        List<Achado> r = new ArrayList<>();
        if (termos.isEmpty()) {
            return r;
        }
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
        pontos.entrySet().stream()
                .filter(e -> e.getValue() * 3 >= maximo)
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limite)
                .forEach(e -> documentos.buscarAtivo(e.getKey()).ifPresent(d -> {
                    String trecho = AssistenteService.melhorTrecho(d, termos);
                    if (trecho != null) {
                        r.add(new Achado(d, trecho, aderenciaTextual(e.getValue(), maximo)));
                    }
                }));
        return r;
    }
}
