package br.com.nexusbuilderlab.semanexus.docia.comparacao;

import br.com.nexusbuilderlab.semanexus.common.exception.DadosInvalidosException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ComparacaoResponse;
import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ComparacaoResponse.CampoMetadado;
import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ComparacaoResponse.DiffLinha;
import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ComparacaoResponse.DiffTrecho;
import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ComparacaoResponse.DocumentoRef;
import br.com.nexusbuilderlab.semanexus.docia.comparacao.dto.ComparacaoResponse.TrechoSemelhante;
import br.com.nexusbuilderlab.semanexus.docia.comparacao.TextoDiff.Op;
import br.com.nexusbuilderlab.semanexus.docia.documento.Documento;
import br.com.nexusbuilderlab.semanexus.docia.documento.DocumentoRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comparação determinística (LCS por linhas e palavras) entre dois documentos. Não usa IA. */
@Service
public class ComparacaoService {

    static final String AVISO = "Comparação determinística (LCS e similaridade de termos), não gerada por IA. "
            + "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.";
    static final int MAX_PALAVRAS = 2000;
    static final int MAX_LINHAS = 1500;
    static final int MAX_TERMOS = 50;
    static final int MAX_TRECHOS = 20;
    static final double LIMIAR_TRECHO = 0.5;

    private final DocumentoRepository documentos;

    public ComparacaoService(DocumentoRepository documentos) {
        this.documentos = documentos;
    }

    @Transactional(readOnly = true)
    public ComparacaoResponse comparar(Long aId, Long bId) {
        Documento a = carregar("aId", aId);
        Documento b = carregar("bId", bId);
        return comparar(a, b);
    }

    Documento carregar(String campo, Long id) {
        Documento d = documentos.buscarAtivo(id).orElseThrow(() -> RecursoNaoEncontradoException.de("Documento", id));
        if (d.getTextoExtraido() == null || d.getTextoExtraido().isBlank()) {
            throw new DadosInvalidosException(campo, "O documento " + id + " (" + d.getTitulo()
                    + ") não possui texto extraído; não é possível comparar o conteúdo.");
        }
        return d;
    }

    ComparacaoResponse comparar(Documento a, Documento b) {
        List<String> pa = TextoDiff.palavras(a.getTextoExtraido());
        List<String> pb = TextoDiff.palavras(b.getTextoExtraido());
        List<String> la = TextoDiff.linhas(a.getTextoExtraido());
        List<String> lb = TextoDiff.linhas(b.getTextoExtraido());
        boolean truncado = pa.size() > MAX_PALAVRAS || pb.size() > MAX_PALAVRAS
                || la.size() > MAX_LINHAS || lb.size() > MAX_LINHAS;
        pa = limitar(pa, MAX_PALAVRAS);
        pb = limitar(pb, MAX_PALAVRAS);
        la = limitar(la, MAX_LINHAS);
        lb = limitar(lb, MAX_LINHAS);

        List<Op> palavras = TextoDiff.diff(pa, pb);
        List<Op> linhas = TextoDiff.diff(la, lb);
        double similaridade = TextoDiff.similaridadeLcs(pa.size(), pb.size(), TextoDiff.iguais(palavras));

        Set<String> ta = TextoDiff.termos(a.getTextoExtraido());
        Set<String> tb = TextoDiff.termos(b.getTextoExtraido());
        Set<String> soA = new TreeSet<>(ta);
        soA.removeAll(tb);
        Set<String> soB = new TreeSet<>(tb);
        soB.removeAll(ta);

        return new ComparacaoResponse(AVISO, false, "LCS por linhas e palavras; Jaccard de termos",
                new DocumentoRef(a.getId(), a.getTitulo()), new DocumentoRef(b.getId(), b.getTitulo()),
                metadados(a, b), similaridade, TextoDiff.jaccard(ta, tb),
                linhas.stream().map(o -> new DiffLinha(o.tipo(), o.texto())).toList(),
                TextoDiff.agrupar(palavras).stream().map(o -> new DiffTrecho(o.tipo(), o.texto())).toList(),
                trechosSemelhantes(a.getTextoExtraido(), b.getTextoExtraido()),
                soA.stream().limit(MAX_TERMOS).toList(), soB.stream().limit(MAX_TERMOS).toList(), truncado);
    }

    private static <T> List<T> limitar(List<T> l, int max) {
        return l.size() > max ? l.subList(0, max) : l;
    }

    private static List<CampoMetadado> metadados(Documento a, Documento b) {
        List<CampoMetadado> r = new ArrayList<>();
        campo(r, "Título", a, b, Documento::getTitulo);
        campo(r, "Categoria", a, b, Documento::getCategoria);
        campo(r, "Tipo", a, b, Documento::getTipo);
        campo(r, "Data do documento", a, b, d -> d.getDataDocumento() == null ? null : d.getDataDocumento().toString());
        campo(r, "Município", a, b, d -> d.getMunicipio() == null ? null : d.getMunicipio().getNome());
        campo(r, "Palavras-chave", a, b, Documento::getPalavrasChave);
        campo(r, "Nome do arquivo", a, b, Documento::getNomeArquivo);
        campo(r, "Formato (MIME)", a, b, Documento::getMime);
        campo(r, "Tamanho (bytes)", a, b, d -> Long.toString(d.getTamanho()));
        campo(r, "Conteúdo (SHA-256)", a, b, Documento::getSha256);
        return r;
    }

    private static void campo(List<CampoMetadado> r, String nome, Documento a, Documento b,
                              Function<Documento, String> f) {
        String va = f.apply(a);
        String vb = f.apply(b);
        r.add(new CampoMetadado(nome, va, vb, Objects.equals(va, vb)));
    }

    /** Para cada sentença de A, a sentença de B mais parecida (Jaccard de termos >= limiar). */
    static List<TrechoSemelhante> trechosSemelhantes(String textoA, String textoB) {
        List<String> sa = sentencas(textoA);
        List<String> sb = sentencas(textoB);
        List<TrechoSemelhante> r = new ArrayList<>();
        for (String x : sa) {
            Set<String> tx = TextoDiff.termos(x);
            if (tx.isEmpty()) {
                continue;
            }
            String melhor = null;
            double melhorSim = 0;
            for (String y : sb) {
                double s = TextoDiff.jaccard(tx, TextoDiff.termos(y)) / 100.0;
                if (s > melhorSim) {
                    melhorSim = s;
                    melhor = y;
                }
            }
            if (melhor != null && melhorSim >= LIMIAR_TRECHO) {
                r.add(new TrechoSemelhante(x, melhor, TextoDiff.arredondar(melhorSim * 100)));
            }
        }
        r.sort((p, q) -> Double.compare(q.similaridade(), p.similaridade()));
        return r.size() > MAX_TRECHOS ? new ArrayList<>(r.subList(0, MAX_TRECHOS)) : r;
    }

    private static List<String> sentencas(String texto) {
        List<String> r = new ArrayList<>();
        for (String linha : TextoDiff.linhas(texto)) {
            for (String s : linha.split("(?<=[.;!?])\\s+")) {
                if (!s.isBlank()) {
                    r.add(s.trim());
                }
            }
        }
        return r;
    }
}
