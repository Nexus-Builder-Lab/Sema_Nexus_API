package br.com.nexusbuilderlab.semanexus.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Catálogo dos ~12 documentos fictícios do seed (PDF/TXT gerados em memória). Textos adaptados do protótipo de
 * referência; todos identificados como fictícios. AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.
 */
final class DemoMassaDocumentos {

    static final String AVISO = "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS. Documento simulado, sem valor oficial.";

    /** {@code demandaChave} refere-se a {@link DemoMassaGerador.Cenario#chave()}. */
    record DocSeed(String titulo, String descricao, String categoria, String tipo, int idadeDias, long municipioId,
                   String palavrasChave, String nomeArquivo, boolean pdf, String texto, String demandaChave) {

        byte[] conteudo() {
            String completo = AVISO + "\n\n" + titulo + "\n\n" + texto + "\n\n" + AVISO;
            return pdf ? DemoMassaDocumentos.pdf(completo) : completo.getBytes(StandardCharsets.UTF_8);
        }
    }

    private DemoMassaDocumentos() {
    }

    static List<DocSeed> catalogo() {
        return List.of(
                new DocSeed("Parecer Técnico nº 014/2026 — versão 1",
                        "Primeira versão do parecer sobre a queimada em Cáceres (fictício).", "Parecer técnico",
                        "Parecer", 38, 5102504L, "parecer, queimada, cáceres, pantanal, versão 1",
                        "parecer-tecnico-014-2026-v1.pdf", true,
                        "Parecer Técnico nº 014/2026 (versão 1).\nAssunto: queimada em pastagem e vegetação nativa, "
                                + "município de Cáceres, bioma Pantanal.\n\nAnálise: a área afetada foi estimada em "
                                + "120 hectares com base em imagens de satélite simuladas. Não há autorização de "
                                + "queima controlada registrada para a propriedade fictícia.\n\nConclusão preliminar: "
                                + "recomenda-se vistoria em campo e notificação do responsável pela área.",
                        "C1"),
                new DocSeed("Parecer Técnico nº 014/2026 — versão 2",
                        "Versão revisada do parecer, com área corrigida e medidas adicionais (fictício).",
                        "Parecer técnico", "Parecer", 33, 5102504L,
                        "parecer, queimada, cáceres, pantanal, versão 2, revisado",
                        "parecer-tecnico-014-2026-v2.pdf", true,
                        "Parecer Técnico nº 014/2026 (versão 2, revisada).\nAssunto: queimada em pastagem e "
                                + "vegetação nativa, município de Cáceres, bioma Pantanal.\n\nAnálise: após a "
                                + "vistoria simulada, a área afetada foi corrigida para 95 hectares, dos quais 30 em "
                                + "área de preservação permanente. Foi identificada a existência de aceiros "
                                + "insuficientes.\n\nConclusão: recomenda-se a lavratura de notificação, a "
                                + "recuperação da área degradada e o monitoramento por 90 dias. Esta versão substitui "
                                + "a versão 1.",
                        "C1"),
                new DocSeed("Relatório de Vistoria — Cáceres",
                        "Relatório da vistoria simulada da queimada em Cáceres (fictício).", "Relatório",
                        "Relatório de vistoria", 30, 5102504L, "vistoria, queimada, cáceres, evidências",
                        "relatorio-vistoria-caceres.txt", false,
                        "Relatório de vistoria (fictício).\nEquipe: Equipe Alfa (demonstrativa).\nLocal: zona rural "
                                + "de Cáceres (fictícia).\nConstatações: vegetação nativa queimada, solo exposto, "
                                + "ausência de aceiros. Registros fotográficos simulados anexados ao processo.",
                        "C1"),
                new DocSeed("Relatório de Monitoramento de Desmatamento — Colniza",
                        "Análise de alertas de desmatamento simulados na região de Colniza (fictício).",
                        "Relatório", "Relatório de monitoramento", 24, 5103254L,
                        "desmatamento, monitoramento, colniza, amazônia, alerta",
                        "relatorio-monitoramento-colniza.pdf", true,
                        "Relatório de monitoramento (fictício).\nPeríodo: últimos 60 dias.\nForam detectados 4 "
                                + "alertas de supressão de vegetação em Colniza, totalizando 210 hectares simulados. "
                                + "Os polígonos serão priorizados para fiscalização em campo.",
                        "C2"),
                new DocSeed("Guia de Procedimentos de Fiscalização Ambiental",
                        "Roteiro fictício de atuação em campo para demandas de desmatamento.", "Procedimento",
                        "Guia", 100, 5103254L, "procedimento, fiscalização, campo, desmatamento, roteiro",
                        "guia-procedimentos-fiscalizacao.txt", false,
                        "Guia de procedimentos (fictício).\n1. Conferir a demanda e o local.\n2. Reunir a equipe e "
                                + "os equipamentos.\n3. Registrar atividades, evidências e resultado.\n4. Encaminhar "
                                + "para conclusão pelo gestor.",
                        "C2"),
                new DocSeed("Ofício de Solicitação de Vistoria — Barão de Melgaço",
                        "Ofício fictício solicitando vistoria por pesca irregular no período de defeso.",
                        "Ofício", "Ofício", 6, 5101605L, "ofício, pesca, defeso, barão de melgaço, pantanal",
                        "oficio-solicitacao-vistoria-barao-melgaco.txt", false,
                        "Ofício (fictício).\nSolicita vistoria em trecho de rio do município de Barão de Melgaço, "
                                + "onde há relato de pesca irregular em período de defeso. Entidade solicitante "
                                + "fictícia.",
                        "C3"),
                new DocSeed("Termo de Denúncia — Sinop",
                        "Registro fictício de denúncia de descarte irregular de resíduos.", "Denúncia",
                        "Termo de denúncia", 1, 5107909L, "denúncia, resíduos, descarte, sinop",
                        "termo-denuncia-sinop.txt", false,
                        "Termo de denúncia (fictício).\nRelato anônimo simulado de descarte de resíduos em terreno "
                                + "baldio de Sinop. Nenhum dado pessoal real consta deste documento.",
                        "C4"),
                new DocSeed("Laudo de Recursos Hídricos — Primavera do Leste",
                        "Laudo fictício sobre captação de água sem outorga.", "Laudo", "Laudo técnico", 82, 5107040L,
                        "laudo, outorga, captação, recursos hídricos, primavera do leste",
                        "laudo-recursos-hidricos-primavera.pdf", true,
                        "Laudo técnico (fictício).\nA captação inspecionada em Primavera do Leste operava sem "
                                + "outorga. Vazão estimada: 12 litros por segundo. Recomenda-se regularização ou "
                                + "suspensão da captação.",
                        "C5"),
                new DocSeed("Relatório Final de Fiscalização — Primavera do Leste",
                        "Relatório final do atendimento da demanda de recursos hídricos (fictício).", "Relatório",
                        "Relatório final", 80, 5107040L, "relatório final, fiscalização, recursos hídricos",
                        "relatorio-final-primavera.pdf", true,
                        "Relatório final (fictício).\nA fiscalização simulada confirmou a captação irregular. O "
                                + "responsável fictício apresentou pedido de regularização. Demanda concluída.",
                        "C5"),
                new DocSeed("Nota Técnica — Focos de Calor em Julho a Outubro",
                        "Nota fictícia sobre a sazonalidade das queimadas no período seco.", "Nota técnica",
                        "Nota técnica", 20, 5106505L, "nota técnica, queimada, sazonalidade, focos de calor, seca",
                        "nota-tecnica-focos-calor.pdf", true,
                        "Nota técnica (fictícia).\nA incidência de queimadas cresce de julho a outubro, com pico "
                                + "em setembro, em razão da estiagem. Recomenda-se reforçar a fiscalização no "
                                + "Pantanal e no Cerrado neste período.",
                        "C6"),
                new DocSeed("Instrução Normativa Demonstrativa sobre Queimadas",
                        "Norma fictícia sobre uso do fogo, criada só para a demonstração.", "Normativo",
                        "Instrução normativa", 120, 5106505L, "norma, queimada, uso do fogo, aceiro, autorização",
                        "instrucao-normativa-queimadas.txt", false,
                        "Instrução normativa (fictícia).\nArt. 1º O uso do fogo exige autorização prévia.\nArt. 2º "
                                + "A propriedade deve manter aceiros. Texto inventado para a demonstração; não "
                                + "reproduz norma real.",
                        "C6"),
                new DocSeed("Boletim Mensal de Focos de Calor (simulado)",
                        "Série mensal fictícia de focos de calor por bioma.", "Boletim", "Boletim", 15, 5106505L,
                        "boletim, focos de calor, bioma, série mensal",
                        "boletim-focos-calor.txt", false,
                        "Boletim (fictício).\nMês; Amazônia; Cerrado; Pantanal\nJulho; 120; 80; 35\nAgosto; 210; "
                                + "150; 60\nSetembro; 340; 260; 95\nOutubro; 180; 140; 50\nValores simulados.",
                        "C6"));
    }

    /** Gera um PDF simples (Helvetica) com quebra de linha e paginação. */
    static byte[] pdf(String texto) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDType1Font fonte = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            List<String> linhas = new ArrayList<>();
            for (String l : texto.split("\n", -1)) {
                linhas.addAll(quebrar(l, 85));
            }
            int i = 0;
            do {
                PDPage pagina = new PDPage(PDRectangle.A4);
                doc.addPage(pagina);
                try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                    cs.beginText();
                    cs.setFont(fonte, 11);
                    cs.setLeading(15);
                    cs.newLineAtOffset(50, 780);
                    for (int k = 0; k < 48 && i < linhas.size(); k++, i++) {
                        cs.showText(linhas.get(i));
                        cs.newLine();
                    }
                    cs.endText();
                }
            } while (i < linhas.size());
            doc.save(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar PDF demonstrativo.", e);
        }
    }

    private static List<String> quebrar(String linha, int max) {
        List<String> r = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String palavra : linha.split(" ")) {
            if (atual.length() + palavra.length() + 1 > max && atual.length() > 0) {
                r.add(atual.toString());
                atual.setLength(0);
            }
            if (atual.length() > 0) {
                atual.append(' ');
            }
            atual.append(palavra);
        }
        r.add(atual.toString());
        return r;
    }
}
