package com.example.notes.auth;

import com.example.notes.support.MySqlTestConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.KeyGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.jwt.ttl-seconds=3600", "app.login-rate-limit.attempts=100"})
@AutoConfigureMockMvc
@Import({MySqlTestConfiguration.class, LoginIT.ProtectedEndpoint.class})
class LoginIT {

    @TestConfiguration(proxyBeanMethods = false)
    @RestController
    static class ProtectedEndpoint {
        @GetMapping("/test/principal")
        String principal(@AuthenticationPrincipal Jwt jwt) {
            return jwt.getSubject();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JwtDecoder decoder;

    @Autowired
    private JwtEncoder encoder;

    @Test
    void registeredUserCanLogInAndUseTheToken() throws Exception {
        String password = "  ewa1234  ";
        String credentials = credentials("Ewa.Login", password);
        String id = json.readTree(postJson("/register", credentials).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).required("id").asText();

        String body = postJson("/login", credentials("ewa.login", password))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsString();

        JsonNode response = json.readTree(body);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.required("expiresIn").longValue()).isEqualTo(3600);
        String token = response.required("token").asText();
        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(id);
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));

        mvc.perform(get("/test/principal").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(id));
    }

    @Test
    void unknownLoginAndWrongPasswordHaveTheSameResponse() throws Exception {
        String login = "known.user";
        postJson("/register", credentials(login, "known-password")).andExpect(status().isCreated());
        for (String attemptedLogin : new String[]{login, "absent.user"}) {
            postJson("/login", credentials(attemptedLogin, "wrong-password"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid login or password."))
                    .andExpect(jsonPath("$.token").doesNotExist());
        }
    }

    @Test
    void rejectsAPasswordLongerThanTheBcryptLimit() throws Exception {
        String password = "a".repeat(72);
        postJson("/register", credentials("long.password", password)).andExpect(status().isCreated());
        postJson("/login", credentials("long.password", password + "a")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"login\":\"ewa\"}", "{\"password\":\"12345678\"}",
            "{\"login\":\"ewa\",\"password\":12345678}"})
    void incompleteOrInvalidJsonGetsBadRequest(String body) throws Exception {
        postJson("/login", body).andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointRequiresABearerToken() throws Exception {
        mvc.perform(get("/test/principal")).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void rejectsAnExpiredToken() throws Exception {
        String expired = signedToken(encoder, JwtConfiguration.ISSUER, Instant.now().minusSeconds(5));
        assertUnauthorized(expired);
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() throws Exception {
        var otherKey = KeyGenerator.getInstance("HmacSHA256").generateKey();
        JwtEncoder otherEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(otherKey));
        assertUnauthorized(signedToken(otherEncoder, JwtConfiguration.ISSUER, Instant.now().plusSeconds(60)));
    }

    @Test
    void rejectsATokenFromAnotherIssuer() throws Exception {
        assertUnauthorized(signedToken(encoder, "urn:other-api", Instant.now().plusSeconds(60)));
    }

    private String signedToken(JwtEncoder signer, String issuer, Instant expiry) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .issuedAt(expiry.minusSeconds(3600))
                .expiresAt(expiry)
                .build();
        var header = JwsHeader.with(JwtConfiguration.ALGORITHM).build();
        return signer.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private void assertUnauthorized(String token) throws Exception {
        mvc.perform(get("/test/principal").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String credentials(String login, String password) throws Exception {
        return json.writeValueAsString(Map.of("login", login, "password", password));
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
