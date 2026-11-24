package br.com.nexusbuilderlab.semanexus.security;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Emite e valida tokens JWT HS256. O token carrega apenas o ID do usuário, o perfil e um identificador único
 * ({@code jti}, usado na revogação pelo logout — Issue #134); nome, e-mail e situação (ativo) são consultados no banco
 * a cada requisição. Nunca registra token nem segredo em log.
 */
public class JwtService {

    public static final int TAMANHO_MINIMO_SEGREDO_BYTES = 32;
    static final String EMISSOR = "sema-nexus-api";
    private static final String CLAIM_PERFIL = "perfil";
    /** Tamanho máximo do {@code jti} aceito (UUID = 36; a coluna TOKEN_REVOGADO.JTI comporta 64). */
    static final int TAMANHO_MAXIMO_JTI = 64;

    /** Dados extraídos de um token válido: {@code jti} identifica o token e {@code expiraEm} é o fim da validade. */
    public record TokenValido(Long usuarioId, Perfil perfil, String jti, Instant expiraEm) {}

    private final byte[] segredo;
    private final Duration expiracao;
    private final Clock clock;

    public JwtService(String segredo, Duration expiracao, Clock clock) {
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_SEGREDO_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET deve ter no mínimo " + TAMANHO_MINIMO_SEGREDO_BYTES + " bytes (caracteres).");
        }
        if (expiracao == null || expiracao.isZero() || expiracao.isNegative()) {
            throw new IllegalStateException("A expiração do JWT deve ser positiva.");
        }
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
        this.expiracao = expiracao;
        this.clock = clock;
    }

    /** Validade do token em segundos (campo {@code expiresIn} da resposta de login). */
    public long expiresInSegundos() {
        return expiracao.toSeconds();
    }

    public String gerar(Usuario usuario) {
        Instant agora = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(EMISSOR)
                .subject(String.valueOf(usuario.getId()))
                .claim(CLAIM_PERFIL, usuario.getPerfil().name())
                .issueTime(Date.from(agora))
                .expirationTime(Date.from(agora.plus(expiracao)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(segredo));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Falha ao assinar o token.", e);
        }
    }

    /**
     * Valida assinatura (somente HS256), emissor, expiração e presença do {@code jti}. Qualquer problema resulta em
     * vazio. A revogação (logout) é conferida à parte, pelo {@link RevogacaoTokenService}.
     */
    public Optional<TokenValido> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new MACVerifier(segredo))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiracaoToken = claims.getExpirationTime();
            if (!EMISSOR.equals(claims.getIssuer())
                    || expiracaoToken == null
                    || !expiracaoToken.toInstant().isAfter(clock.instant())) {
                return Optional.empty();
            }
            String jti = claims.getJWTID();
            if (jti == null || jti.isBlank() || jti.length() > TAMANHO_MAXIMO_JTI) {
                return Optional.empty();
            }
            Long id = Long.valueOf(claims.getSubject());
            Perfil perfil = Perfil.valueOf(claims.getStringClaim(CLAIM_PERFIL));
            return Optional.of(new TokenValido(id, perfil, jti, expiracaoToken.toInstant()));
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
