package com.example.notes.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {

    static final String ISSUER = "urn:notes-api";
    static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

    @Bean
    SecretKey jwtSigningKey(@Value("${app.jwt.secret}") String encodedKey) {
        byte[] key = Base64.getDecoder().decode(encodedKey);
        if (key.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 random bytes encoded in Base64.");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(ALGORITHM).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(ISSUER),
                new JwtTimestampValidator(Duration.ZERO),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, StringUtils::hasText)));
        return decoder;
    }
}
