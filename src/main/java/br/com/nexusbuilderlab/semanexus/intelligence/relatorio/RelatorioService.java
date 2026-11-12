package br.com.nexusbuilderlab.semanexus.intelligence.relatorio;

import br.com.nexusbuilderlab.semanexus.common.dto.AvisoDemonstrativo;
import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.flux.demanda.Demanda;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.IndicadorRepository;
import br.com.nexusbuilderlab.semanexus.intelligence.indicador.dto.IndicadorFiltro;
import br.com.nexusbuilderlab.semanexus.intelligence.relatorio.dto.LinhaRelatorioDemanda;
import br.com.nexusbuilderlab.semanexus.intelligence.relatorio.dto.RelatorioDemandasResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Relatório de demandas filtrável (JSON paginado e CSV). Dados simulados. */
@Service
@Transactional(readOnly = true)
public class RelatorioService {

    static final int TAMANHO_MAXIMO = 100;
    static final String BOM = "﻿";
    static final String CABECALHO = "Número;Título;Categoria;Município;Bioma;Situação;Prioridade;"
            + "Data de recebimento;Prazo;Concluída em";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final IndicadorRepository repositorio;

    public RelatorioService(IndicadorRepository repositorio) {
        this.repositorio = repositorio;
    }

    public RelatorioDemandasResponse pagina(IndicadorFiltro filtro, int page, int size) {
        validar(filtro);
        int p = Math.max(0, page);
        int s = Math.min(TAMANHO_MAXIMO, Math.max(1, size));
        long total = repositorio.contarDemandas(filtro);
        List<LinhaRelatorioDemanda> linhas = repositorio.demandasPagina(filtro, p * s, s).stream()
                .map(RelatorioService::linha).toList();
        return new RelatorioDemandasResponse(true, AvisoDemonstrativo.TEXTO, linhas, p, s, total,
                (int) ((total + s - 1) / s));
    }

    /** CSV UTF-8 com BOM, separador ";", aviso demonstrativo na primeira linha. */
    public byte[] csv(IndicadorFiltro filtro) {
        validar(filtro);
        String corpo = repositorio.demandasPagina(filtro, 0, Integer.MAX_VALUE).stream()
                .map(RelatorioService::linha).map(RelatorioService::registro)
                .map(r -> r + "\r\n").collect(Collectors.joining());
        String csv = BOM + AvisoDemonstrativo.TEXTO + "\r\n" + CABECALHO + "\r\n" + corpo;
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    private static void validar(IndicadorFiltro f) {
        if (f != null && f.de() != null && f.ate() != null && f.de().isAfter(f.ate())) {
            throw new DadosInvalidosException("de", "A data inicial não pode ser posterior à data final.");
        }
    }

    static LinhaRelatorioDemanda linha(Demanda d) {
        return new LinhaRelatorioDemanda(d.getNumero(), d.getTitulo(), d.getCategoria().rotulo(),
                d.getMunicipio().getNome(), d.getMunicipio().getBioma().rotulo(), d.getStatus().rotulo(),
                d.getPrioridade() == null ? "" : d.getPrioridade().rotulo(), d.getDataRecebimento(), d.getPrazo(),
                d.getConcluidaEm() == null ? null : d.getConcluidaEm().toLocalDate());
    }

    static String registro(LinhaRelatorioDemanda l) {
        return String.join(";", celula(l.numero()), celula(l.titulo()), celula(l.categoria()),
                celula(l.municipio()), celula(l.bioma()), celula(l.status()), celula(l.prioridade()),
                data(l.dataRecebimento()), data(l.prazo()), data(l.concluidaEm()));
    }

    private static String data(LocalDate d) {
        return d == null ? "" : DATA.format(d);
    }

    /** Escapa aspas/separadores e neutraliza fórmulas (=, +, -, @) abertas no Excel. */
    static String celula(String v) {
        if (v == null || v.isEmpty()) {
            return "";
        }
        String t = v;
        char c = t.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            t = "'" + t;
        }
        if (t.contains(";") || t.contains("\"") || t.contains("\n") || t.contains("\r")) {
            t = "\"" + t.replace("\"", "\"\"") + "\"";
        }
        return t;
    }
}
