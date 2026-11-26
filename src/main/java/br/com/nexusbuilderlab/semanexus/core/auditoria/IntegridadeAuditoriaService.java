package br.com.nexusbuilderlab.semanexus.core.auditoria;

import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoria.Falha;
import br.com.nexusbuilderlab.semanexus.core.auditoria.CadeiaAuditoriaRepositorio.Estado;
import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.IntegridadeAuditoria;
import br.com.nexusbuilderlab.semanexus.core.auditoria.dto.IntegridadeAuditoria.FalhaIntegridade;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

/**
 * Confere a cadeia SHA-256 da auditoria (Issue #148). Somente leitura.
 *
 * <p>Lê primeiro o estado da cadeia e depois percorre só as posições até a última registrada nele: selagens que
 * terminem durante a conferência ficam com posições maiores e não interferem (registros já selados nunca são
 * reescritos pela selagem), então não há falso alarme por concorrência.
 */
@Service
public class IntegridadeAuditoriaService {

    static final ZoneId FUSO = ZoneId.of("America/Cuiaba");

    private final CadeiaAuditoriaRepositorio cadeia;
    private final Clock clock;

    public IntegridadeAuditoriaService(CadeiaAuditoriaRepositorio cadeia, Clock clock) {
        this.cadeia = cadeia;
        this.clock = clock;
    }

    public IntegridadeAuditoria verificar() {
        Estado estado = cadeia.estado();
        CadeiaAuditoria.Verificador verificador = new CadeiaAuditoria.Verificador();
        cadeia.percorrerSelados(estado.ultimaSeq(), verificador::aceitar);
        verificador.concluir(estado.ultimaSeq(), estado.ultimoHash());
        Falha falha = verificador.falha();
        return new IntegridadeAuditoria(falha == null, falha == null ? "INTEGRA" : "VIOLADA",
                CadeiaAuditoria.ALGORITMO, verificador.verificados(), cadeia.contarLegados(estado.inicioId()),
                cadeia.contarPendentes(estado.inicioId()), estado.ultimaSeq(), estado.ultimoHash(),
                falha == null ? null : new FalhaIntegridade(falha.seq(), falha.auditoriaId(), falha.motivo().name(),
                        descricao(falha.motivo())),
                LocalDateTime.now(clock.withZone(FUSO)));
    }

    private static String descricao(CadeiaAuditoria.Motivo motivo) {
        return switch (motivo) {
            case HASH_DIVERGENTE -> "O conteúdo do registro não corresponde ao hash gravado (registro alterado).";
            case ENCADEAMENTO_QUEBRADO -> "O hash anterior gravado não é o do registro anterior "
                    + "(registro removido, inserido ou reordenado).";
            case SEQUENCIA_AUSENTE -> "Falta uma posição da cadeia (registro removido ou selagem apagada).";
            case FIM_DIVERGENTE -> "O fim da cadeia não confere com o estado registrado "
                    + "(registros finais removidos ou alterados).";
        };
    }
}
