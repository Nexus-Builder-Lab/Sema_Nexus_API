package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.AuditoriaDto;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoService;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoService.DocumentoVinculado;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FotoFiscalizacao;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.HistoricoItem;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta e exporta o dossiê completo da demanda em PDF (Issue #146).
 * Respeita integralmente o escopo da demanda (404 se inexistente, 403 para fiscal fora da equipe/atribuição)
 * e registra a emissão na trilha de auditoria com código de verificação determinístico sobre o retrato emitido.
 */
@Service
public class DossieDemandaService {

    /** Faixa obrigatória exibida no PDF demonstrativo. */
    public static final String FAIXA_DEMONSTRATIVA = "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS — SEM VALOR OFICIAL";
    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DemandaService demandas;
    private final DocumentoService documentos;
    private final FotoFiscalizacaoService fotos;
    private final AuditService auditoria;
    private final Clock clock;

    public DossieDemandaService(DemandaService demandas, DocumentoService documentos,
                                FotoFiscalizacaoService fotos, AuditService auditoria, Clock clock) {
        this.demandas = demandas;
        this.documentos = documentos;
        this.fotos = fotos;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** Resultado binário do dossiê pronto para download. */
    public record PdfDossie(String nomeArquivo, byte[] conteudo, String codigoVerificacao) {
    }

    /**
     * Gera o dossiê em PDF da demanda informada, registrando a emissão na auditoria.
     */
    @Transactional
    public PdfDossie gerar(UsuarioAutenticado ator, Long id) {
        DemandaDetalhe demanda = demandas.detalhar(ator, id); // valida existência (404) e escopo (403)
        List<HistoricoItem> historico = demandas.historico(ator, id);
        List<DocumentoVinculado> docs = documentos.listarVinculadosParaDossie(ator, id);
        List<FotoFiscalizacao> fotosList = fotos.listar(ator, id);
        List<AuditoriaDto> registrosAuditoria = auditoria.historicoDaEntidade(DemandaService.ENTIDADE, String.valueOf(id));

        LocalDateTime emitidoEm = LocalDateTime.now(clock.withZone(DemandaService.FUSO));
        String codigoVerificacao = calcularCodigoVerificacao(
                demanda, historico, docs, fotosList, registrosAuditoria, ator, emitidoEm);
        String payloadQr = montarPayloadQr(demanda, emitidoEm, codigoVerificacao);

        auditoria.registrar("EXPORTAR_DOSSIE", DemandaService.ENTIDADE, String.valueOf(id),
                "Dossiê PDF da demanda %s emitido; verificação %s".formatted(demanda.numero(), codigoVerificacao));

        byte[] pdf = DossiePdfGerador.gerar(
                demanda, historico, docs, fotosList, registrosAuditoria, ator, emitidoEm, codigoVerificacao, payloadQr);
        String nomeArquivo = "dossie-" + demanda.numero().replaceAll("[^A-Za-z0-9._-]", "-") + ".pdf";
        return new PdfDossie(nomeArquivo, pdf, codigoVerificacao);
    }

    static String montarPayloadQr(DemandaDetalhe demanda, LocalDateTime emitidoEm, String codigoVerificacao) {
        return "SEMA-ALERTA|DEMO|DEMANDA=" + demanda.id()
                + "|NUMERO=" + demanda.numero()
                + "|STATUS=" + demanda.status().name()
                + "|EMITIDO=" + ISO_LOCAL.format(emitidoEm)
                + "|VERIFICACAO=" + codigoVerificacao;
    }

    static String calcularCodigoVerificacao(DemandaDetalhe d, List<HistoricoItem> historico,
                                            List<DocumentoVinculado> docs, List<FotoFiscalizacao> fotos,
                                            List<AuditoriaDto> registrosAuditoria,
                                            UsuarioAutenticado ator, LocalDateTime emitidoEm) {
        StringBuilder sb = new StringBuilder();
        sb.append(d.id()).append('|').append(d.numero()).append('|').append(d.status().name())
                .append('|').append(d.atualizadoEm()).append('|').append(ISO_LOCAL.format(emitidoEm))
                .append('|').append(ator.id())
                .append('|').append(historico.size())
                .append('|').append(registrosAuditoria.isEmpty() ? 0L : registrosAuditoria.get(0).id());
        for (DocumentoVinculado doc : docs) {
            sb.append("|DOC:").append(doc.id()).append(':').append(doc.sha256());
        }
        for (FotoFiscalizacao f : fotos) {
            sb.append("|FOTO:").append(f.id()).append(':').append(f.sha256());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(digest).substring(0, 24).toUpperCase(Locale.ROOT);
            return hex.substring(0, 6) + "-" + hex.substring(6, 12)
                    + "-" + hex.substring(12, 18) + "-" + hex.substring(18, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
