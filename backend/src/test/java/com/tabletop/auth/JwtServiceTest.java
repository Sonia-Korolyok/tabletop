package com.tabletop.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tabletop.config.JwtProperties;
import com.tabletop.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final SecretKeySpec key = new SecretKeySpec(
            "test-secret-test-secret-test-secret-1234".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    @Test
    void issuesTokenWithUserIdAsSubjectAndConfiguredTtl() {
        Instant now = Instant.now();
        JwtService service = new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(key)),
                new JwtProperties("ignored", 60), Clock.fixed(now, ZoneOffset.UTC));
        User user = new User("sonia@example.com", "hash", "Sonia");
        ReflectionTestUtils.setField(user, "id", 42L);

        AuthDtos.TokenResponse response = service.issue(user);

        Jwt jwt = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
                .decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("sonia@example.com");
        assertThat(response.expiresInSeconds()).isEqualTo(3600);
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }
}
