package com.personal.medisynth.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.personal.medisynth.model.Role;
import com.personal.medisynth.model.Users;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test: no Spring context and no database. */
class JwtServiceTest {

    private String secret;
    private JwtService service;
    private Users user;

    @BeforeEach
    void setUp() {
        secret = randomSecret(48);
        service = newService(secret, 3_600_000L);
        user = new Users();
        user.setId(42L);
        user.setGrantedRole(Role.PATIENT);
    }

    private static String randomSecret(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    private static JwtService newService(String secret, long expirationMs) {
        JwtService s = new JwtService();
        ReflectionTestUtils.setField(s, "secret", secret);
        ReflectionTestUtils.setField(s, "expirationMs", expirationMs);
        ReflectionTestUtils.setField(s, "issuer", "medisynth");
        s.init();
        return s;
    }

    private String token(String signingSecret, String issuer, Instant expiry) {
        return Jwts.builder().issuer(issuer).subject("42").issuedAt(Date.from(expiry.minus(1, ChronoUnit.HOURS)))
                .expiration(Date.from(expiry))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(signingSecret)), Jwts.SIG.HS256).compact();
    }

    @Test
    void roundTripReturnsTheUserId() {
        assertThat(service.extractUserId(service.generateToken(user))).contains(42L);
    }

    @Test
    void expiredTokenIsRejected() {
        assertThat(service.extractUserId(token(secret, "medisynth", Instant.now().minusSeconds(60)))).isEmpty();
        assertThat(service.extractUserId(token(secret, "medisynth", Instant.now().plusSeconds(600)))).contains(42L);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = token(randomSecret(48), "medisynth", Instant.now().plusSeconds(600));
        assertThat(service.extractUserId(foreign)).isEmpty();
    }

    @Test
    void wrongIssuerTamperingAndGarbageAreRejected() {
        assertThat(service.extractUserId(token(secret, "other", Instant.now().plusSeconds(600)))).isEmpty();
        String good = service.generateToken(user);
        assertThat(service.extractUserId(good.substring(0, good.length() - 3) + "abc")).isEmpty();
        assertThat(service.extractUserId("garbage")).isEmpty();
        assertThat(service.extractUserId("")).isEmpty();
        assertThat(service.extractUserId(null)).isEmpty();
    }

    @Test
    void weakOrMissingSecretsPreventStartup() {
        assertThatThrownBy(() -> newService(randomSecret(16), 1000L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> newService("   ", 1000L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> newService("!!!not base64!!!", 1000L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> newService(secret, 0L)).isInstanceOf(IllegalStateException.class);
    }
}
