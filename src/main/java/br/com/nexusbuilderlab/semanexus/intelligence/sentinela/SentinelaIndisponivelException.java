package br.com.nexusbuilderlab.semanexus.intelligence.sentinela;

/** Ainda não há briefing e a geração falhou (ou está em espera após falha): 503 com {@code Retry-After}. */
public class SentinelaIndisponivelException extends RuntimeException {

    private final long retryAfterSegundos;

    public SentinelaIndisponivelException(long retryAfterSegundos) {
        super("O briefing da Sentinela não pôde ser gerado agora; tente novamente em instantes.");
        this.retryAfterSegundos = retryAfterSegundos;
    }

    public long retryAfterSegundos() {
        return retryAfterSegundos;
    }
}
