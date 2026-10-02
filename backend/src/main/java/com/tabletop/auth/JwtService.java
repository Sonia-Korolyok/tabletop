package com.tabletop.auth;

import com.tabletop.config.JwtProperties;
import com.tabletop.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public JwtService(JwtEncoder encoder, JwtProperties props) {
        this(encoder, props, Clock.systemUTC());
    }

    JwtService(JwtEncoder encoder, JwtProperties props, Clock clock) {
        this.encoder = encoder;
        this.ttl = Duration.ofMinutes(props.ttlMinutes());
        this.clock = clock;
    }

    public AuthDtos.TokenResponse issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("tabletop")
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthDtos.TokenResponse(token, "Bearer", ttl.toSeconds());
    }
}
