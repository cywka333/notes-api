package com.example.notes.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final long ttlSeconds;

    AccessTokenService(JwtEncoder encoder, @Value("${app.jwt.ttl-seconds}") long ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("JWT_TTL_SECONDS must be positive.");
        }
        this.encoder = encoder;
        this.ttlSeconds = ttlSeconds;
    }

    public IssuedAccessToken issue(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfiguration.ISSUER)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .build();
        JwsHeader header = JwsHeader.with(JwtConfiguration.ALGORITHM).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, ttlSeconds);
    }
}
