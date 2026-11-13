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
 */
@Component
public class BuscaDocumentos {

    private final JdbcTemplate jdbc;
    private final DocumentoRepository documentos;

    public BuscaDocumentos(JdbcTemplate jdbc, DocumentoRepository documentos) {
        this.jdbc = jdbc;
        this.documentos = documentos;
    }

    /** Documento encontrado e o trecho (frase) com mais termos da consulta. */
    public record Achado(Documento documento, String trecho) {
    }

    public List<Achado> buscar(String consulta, int limite) {
        List<String> termos = AssistenteService.termos(DocumentoSpecification.normalizar(consulta == null ? "" : consulta.strip()));
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
        }
        pontos.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limite)
                .forEach(e -> documentos.buscarAtivo(e.getKey()).ifPresent(d -> {
                    String trecho = AssistenteService.melhorTrecho(d, termos);
                    if (trecho != null) {
                        r.add(new Achado(d, trecho));
                    }
                }));
        return r;
    }
}
