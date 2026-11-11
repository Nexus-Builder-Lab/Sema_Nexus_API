package br.com.nexusbuilderlab.semanexus.flux.demanda;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gera o número sequencial da demanda ({@code DEM-AAAA/NNNN}) com lock pessimista na linha do ano em
 * {@code NUMERACAO_DEMANDA}. Deve rodar na mesma transação que grava a demanda: o lock só é liberado no commit,
 * então requisições concorrentes nunca recebem o mesmo número e uma falha na gravação não consome sequencial.
 */
@Service
public class NumeracaoService {

    private final NumeracaoDemandaRepository repositorio;
    private final TransactionTemplate novaTransacao;

    public NumeracaoService(NumeracaoDemandaRepository repositorio, PlatformTransactionManager transacoes) {
        this.repositorio = repositorio;
        this.novaTransacao = new TransactionTemplate(transacoes);
        this.novaTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Reserva e devolve o próximo número do ano informado. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String proximoNumero(int ano) {
        NumeracaoDemanda numeracao = repositorio.buscarComLock(ano).orElseGet(() -> criarEBloquear(ano));
        return formatar(ano, numeracao.proximo());
    }

    static String formatar(int ano, long sequencial) {
        return String.format(Locale.ROOT, "DEM-%04d/%04d", ano, sequencial);
    }

    /**
     * Primeiro número do ano: cria a linha em transação própria (já commitada, visível às concorrentes) e então
     * a bloqueia. Se outra requisição criou a linha antes, a violação da PK é ignorada e a linha é bloqueada.
     */
    private NumeracaoDemanda criarEBloquear(int ano) {
        try {
            novaTransacao.executeWithoutResult(status -> repositorio.saveAndFlush(new NumeracaoDemanda(ano)));
        } catch (DataIntegrityViolationException jaCriada) {
            // outra transação criou a linha do ano ao mesmo tempo: basta bloqueá-la abaixo
        }
        return repositorio.buscarComLock(ano)
                .orElseThrow(() -> new IllegalStateException("Linha de numeração do ano " + ano + " não encontrada."));
    }
}
