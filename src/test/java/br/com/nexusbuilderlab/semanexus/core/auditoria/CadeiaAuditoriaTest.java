package br.com.nexusbuilderlab.semanexus.core.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoria.Motivo;
import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoria.Registro;
import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoria.Selado;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cadeia SHA-256 da auditoria (Issue #148): íntegra e as formas de adulteração detectadas. */
class CadeiaAuditoriaTest {

    static final LocalDateTime DATA = LocalDateTime.of(2026, 11, 25, 10, 30, 15, 123456000);

    static Registro registro(long id, String detalhe) {
        return new Registro(id, DATA.plusSeconds(id), id % 2 == 0 ? null : 7L, "EDITAR", "DEMANDA",
                String.valueOf(id), detalhe, "{\"titulo\":\"A\"}", "{\"titulo\":\"B\"}");
    }

    /** Sela os registros como o SeladorAuditoria faz. */
    static List<Selado> selar(List<Registro> registros) {
        List<Selado> r = new ArrayList<>();
        String anterior = CadeiaAuditoria.GENESE;
        long seq = 0;
        for (Registro reg : registros) {
            seq++;
            String h = CadeiaAuditoria.hash(seq, anterior, reg);
            r.add(new Selado(reg, seq, anterior, h));
            anterior = h;
        }
        return r;
    }

    static CadeiaAuditoria.Verificador verificar(List<Selado> selados, long ultimaSeq, String ultimoHash) {
        var v = new CadeiaAuditoria.Verificador();
        for (Selado s : selados) {
            if (!v.aceitar(s)) {
                break;
            }
        }
        v.concluir(ultimaSeq, ultimoHash);
        return v;
    }

    static List<Selado> cadeia(int n) {
        List<Registro> regs = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            regs.add(registro(100 + i, "detalhe " + i));
        }
        return selar(regs);
    }

    private static Registro comDetalhe(Registro r, String detalhe) {
        return new Registro(r.id(), r.data(), r.usuarioId(), r.acao(), r.entidade(), r.entidadeId(), detalhe,
                r.valorAnterior(), r.valorNovo());
    }

    @Test
    void hashEhSha256HexDeterministicoESensivelAQualquerCampo() {
        Registro r = registro(1, "d");
        String h = CadeiaAuditoria.hash(1, CadeiaAuditoria.GENESE, r);
        assertThat(h).hasSize(64).matches("[0-9a-f]{64}")
                .isEqualTo(CadeiaAuditoria.hash(1, CadeiaAuditoria.GENESE, r));
        assertThat(CadeiaAuditoria.hash(2, CadeiaAuditoria.GENESE, r)).isNotEqualTo(h);
        assertThat(CadeiaAuditoria.hash(1, CadeiaAuditoria.GENESE, comDetalhe(r, "e"))).isNotEqualTo(h);
        Registro outraData = new Registro(1, r.data().plusNanos(1000), r.usuarioId(), r.acao(), r.entidade(),
                r.entidadeId(), r.detalhe(), r.valorAnterior(), r.valorNovo());
        assertThat(CadeiaAuditoria.hash(1, CadeiaAuditoria.GENESE, outraData)).isNotEqualTo(h);
    }

    @Test
    void formaCanonicaNaoEhAmbigua() {
        // Mesmo texto concatenado, fronteira diferente entre campos: hashes diferentes.
        Registro a = new Registro(1, DATA, null, "AB", "C", null, null, null, null);
        Registro b = new Registro(1, DATA, null, "A", "BC", null, null, null, null);
        Registro nulo = new Registro(1, DATA, null, "A", "B", null, null, null, null);
        Registro vazio = new Registro(1, DATA, null, "A", "B", "", null, null, null);
        assertThat(CadeiaAuditoria.hash(1, "x", a)).isNotEqualTo(CadeiaAuditoria.hash(1, "x", b));
        assertThat(CadeiaAuditoria.hash(1, "x", nulo)).isNotEqualTo(CadeiaAuditoria.hash(1, "x", vazio));
    }

    @Test
    void cadeiaIntegraEhAceita() {
        List<Selado> c = cadeia(5);
        var v = verificar(c, 5, c.get(4).hashRegistro());
        assertThat(v.falha()).isNull();
        assertThat(v.verificados()).isEqualTo(5);
        assertThat(c.get(0).hashAnterior()).isEqualTo(CadeiaAuditoria.GENESE);
    }

    @Test
    void cadeiaVaziaEhIntegra() {
        assertThat(verificar(List.of(), 0, null).falha()).isNull();
    }

    @Test
    void registroAlteradoEhDetectado() {
        List<Selado> c = new ArrayList<>(cadeia(5));
        Selado s = c.get(2);
        c.set(2, new Selado(comDetalhe(s.registro(), "detalhe adulterado"), s.seq(), s.hashAnterior(),
                s.hashRegistro()));

        var f = verificar(c, 5, c.get(4).hashRegistro()).falha();
        assertThat(f.motivo()).isEqualTo(Motivo.HASH_DIVERGENTE);
        assertThat(f.seq()).isEqualTo(3);
        assertThat(f.auditoriaId()).isEqualTo(s.registro().id());
    }

    @Test
    void valorAntesDepoisAlteradoEhDetectado() {
        List<Selado> c = new ArrayList<>(cadeia(3));
        Selado s = c.get(0);
        Registro r = s.registro();
        c.set(0, new Selado(new Registro(r.id(), r.data(), r.usuarioId(), r.acao(), r.entidade(), r.entidadeId(),
                r.detalhe(), r.valorAnterior(), "{\"titulo\":\"Z\"}"), s.seq(), s.hashAnterior(), s.hashRegistro()));
        assertThat(verificar(c, 3, c.get(2).hashRegistro()).falha().motivo()).isEqualTo(Motivo.HASH_DIVERGENTE);
    }

    @Test
    void registroRemovidoNoMeioEhDetectado() {
        List<Selado> c = new ArrayList<>(cadeia(5));
        c.remove(2);
        var f = verificar(c, 5, c.get(3).hashRegistro()).falha();
        assertThat(f.motivo()).isEqualTo(Motivo.SEQUENCIA_AUSENTE);
        assertThat(f.seq()).isEqualTo(3);
    }

    @Test
    void registroRecalculadoSemRefazerOEncadeamentoEhDetectado() {
        // O adulterador recalcula o hash do registro alterado, mas o seguinte continua apontando para o hash antigo.
        List<Selado> c = new ArrayList<>(cadeia(4));
        Selado s = c.get(1);
        Registro novo = comDetalhe(s.registro(), "adulterado");
        c.set(1, new Selado(novo, s.seq(), s.hashAnterior(), CadeiaAuditoria.hash(s.seq(), s.hashAnterior(), novo)));
        var f = verificar(c, 4, c.get(3).hashRegistro()).falha();
        assertThat(f.motivo()).isEqualTo(Motivo.ENCADEAMENTO_QUEBRADO);
        assertThat(f.seq()).isEqualTo(3);
    }

    @Test
    void registrosFinaisRemovidosSaoDetectadosPeloEstado() {
        List<Selado> c = cadeia(5);
        var f = verificar(c.subList(0, 3), 5, c.get(4).hashRegistro()).falha();
        assertThat(f.motivo()).isEqualTo(Motivo.FIM_DIVERGENTE);
        assertThat(f.seq()).isEqualTo(4);
    }

    @Test
    void ultimoHashDivergenteDoEstadoEhDetectado() {
        assertThat(verificar(cadeia(3), 3, "f".repeat(64)).falha().motivo()).isEqualTo(Motivo.FIM_DIVERGENTE);
    }
}
