package br.com.nexusbuilderlab.semanexus.security;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revogação de JWT (Issue #134). O logout grava o {@code jti} do token em {@code TOKEN_REVOGADO} (Oracle, sobrevive a
 * reinícios da API) com a data de expiração; o {@link JwtAuthFilter} recusa (401) qualquer token revogado. Um cache
 * local guarda os revogados já conhecidos para evitar consultas repetidas; a fonte da verdade é o banco.
 * Nunca registra o token nem o {@code jti} em log.
 */
@Service
public class RevogacaoTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RevogacaoTokenService.class);
    /** Limite do cache local de revogados (acima disso, os expirados são descartados). */
    static final int LIMITE_CACHE = 10_000;

    private final TokenRevogadoRepository repositorio;
    private final Clock clock;
    /** jti → expiração dos tokens sabidamente revogados. */
    private final Map<String, Instant> revogadosConhecidos = new ConcurrentHashMap<>();

    public RevogacaoTokenService(TokenRevogadoRepository repositorio, Clock clock) {
        this.repositorio = repositorio;
        this.clock = clock;
    }

    /** true quando o token foi revogado (logout) e ainda não expirou. */
    @Transactional(readOnly = true)
    public boolean revogado(String jti) {
        if (jti == null || jti.isBlank()) {
            return true;
        }
        if (revogadosConhecidos.containsKey(jti)) {
            return true;
        }
        return repositorio.findById(jti)
                .map(t -> {
                    lembrar(jti, t.getExpiraEm().toInstant(ZoneOffset.UTC));
                    return true;
                })
                .orElse(false);
    }

    /** Revoga o token na hora. Idempotente: revogar de novo (ou em paralelo) não falha. */
    @Transactional
    public void revogar(String jti, Long usuarioId, Instant expiraEm) {
        if (jti == null || jti.isBlank() || expiraEm == null) {
            return;
        }
        lembrar(jti, expiraEm);
        if (repositorio.existsById(jti)) {
            return;
        }
        try {
            repositorio.saveAndFlush(new TokenRevogado(jti, usuarioId, LocalDateTime.ofInstant(expiraEm, ZoneOffset.UTC),
                    LocalDateTime.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // Outra requisição revogou o mesmo token ao mesmo tempo: o resultado é o mesmo.
            LOG.debug("Token já revogado por requisição concorrente.");
        }
    }

    /** Limpeza diária (03:17 UTC) dos tokens revogados que já expiraram. */
    @Scheduled(cron = "${TOKEN_REVOGADO_LIMPEZA_CRON:0 17 3 * * *}", zone = "UTC")
    @Transactional
    public int limparExpirados() {
        Instant agora = clock.instant();
        revogadosConhecidos.values().removeIf(exp -> !exp.isAfter(agora));
        int removidos = repositorio.removerExpirados(LocalDateTime.ofInstant(agora, ZoneOffset.UTC));
        if (removidos > 0) {
            LOG.info("Limpeza de tokens revogados: {} registro(s) expirado(s) removido(s).", removidos);
        }
        return removidos;
    }

    private void lembrar(String jti, Instant expiraEm) {
        if (revogadosConhecidos.size() >= LIMITE_CACHE) {
            Instant agora = clock.instant();
            revogadosConhecidos.values().removeIf(exp -> !exp.isAfter(agora));
            if (revogadosConhecidos.size() >= LIMITE_CACHE) {
                return; // o banco continua sendo consultado; o cache é só otimização
            }
        }
        revogadosConhecidos.put(jti, expiraEm);
    }
}
