package br.com.nexusbuilderlab.semanexus.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres!!";
    static final Instant AGORA = Instant.parse("2026-11-10T12:00:00Z");

    private final Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
    private final JwtService servico = new JwtService(SEGREDO, Duration.ofHours(8), relogio);

    private static Usuario usuario(long id, Perfil perfil) {
        Usuario u = new Usuario("Fulano Fictício", "fulano@exemplo.test", "hash", perfil);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    void geraTokenValidoComIdEPerfil() {
        String token = servico.gerar(usuario(7, Perfil.GESTOR));

        var valido = servico.validar(token);

        assertThat(valido).isPresent();
        assertThat(valido.get().usuarioId()).isEqualTo(7L);
        assertThat(valido.get().perfil()).isEqualTo(Perfil.GESTOR);
        assertThat(servico.expiresInSegundos()).isEqualTo(28_800L);
    }

    @Test
    void tokenNaoCarregaSenhaNemEmail() throws Exception {
        String token = servico.gerar(usuario(7, Perfil.ADMIN));
        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(claims.getClaims().keySet()).containsExactlyInAnyOrder("iss", "sub", "perfil", "iat", "exp", "jti");
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(AGORA.plus(Duration.ofHours(8)));
    }

    @Test
    void tokenExpiradoERejeitado() {
        String token = servico.gerar(usuario(1, Perfil.ADMIN));
        Clock nove = Clock.fixed(AGORA.plus(Duration.ofHours(9)), ZoneOffset.UTC);

        assertThat(new JwtService(SEGREDO, Duration.ofHours(8), nove).validar(token)).isEmpty();
    }

    @Test
    void tokenAdulteradoOuDeOutroSegredoERejeitado() {
        String token = servico.gerar(usuario(1, Perfil.CONSULTA));
        JwtService outro = new JwtService("outro-segredo-diferente-com-32-caracteres!!", Duration.ofHours(8), relogio);

        assertThat(outro.validar(token)).isEmpty();
        String adulterado = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
        assertThat(servico.validar(adulterado)).isEmpty();
    }

    @Test
    void tokenComAlgoritmoDiferenteDeHs256ERejeitado() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(JwtService.EMISSOR).subject("1").claim("perfil", "ADMIN")
                .expirationTime(Date.from(AGORA.plus(Duration.ofHours(1)))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claims);
        // HS512 exige chave de 64 bytes; assinado com o mesmo prefixo do segredo, só o algoritmo difere.
        jwt.sign(new MACSigner((SEGREDO + SEGREDO).getBytes(StandardCharsets.UTF_8)));

        assertThat(servico.validar(jwt.serialize())).isEmpty();
    }

    @Test
    void tokenSemAssinaturaAlgNoneERejeitado() {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String cabecalho = b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String corpo = b64.encodeToString(
                "{\"iss\":\"sema-nexus-api\",\"sub\":\"1\",\"perfil\":\"ADMIN\",\"exp\":9999999999}"
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(servico.validar(cabecalho + "." + corpo + ".")).isEmpty();
    }

    @Test
    void entradasInvalidasResultamEmVazio() {
        assertThat(servico.validar(null)).isEmpty();
        assertThat(servico.validar("")).isEmpty();
        assertThat(servico.validar("lixo")).isEmpty();
    }

    @Test
    void segredoCurtoOuAusenteERecusado() {
        assertThatThrownBy(() -> new JwtService("curto", Duration.ofHours(8), relogio))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32");
        assertThatThrownBy(() -> new JwtService(null, Duration.ofHours(8), relogio))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiracaoInvalidaERecusada() {
        assertThatThrownBy(() -> new JwtService(SEGREDO, Duration.ZERO, relogio))
                .isInstanceOf(IllegalStateException.class);
    }
}
