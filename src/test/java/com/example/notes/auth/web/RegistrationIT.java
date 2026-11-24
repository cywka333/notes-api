package com.example.notes.auth.web;

import com.example.notes.support.MySqlTestConfiguration;
import com.example.notes.user.User;
import com.example.notes.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class RegistrationIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registersWithoutATokenAndReturnsOnlyThePublicFields() throws Exception {
        String login = "Ewa.Nowak";
        String password = " password ";

        String body = register(login, password).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode response = json.readTree(body);
        User saved = users.findByLogin(login).orElseThrow();

        assertThat(response.size()).isEqualTo(3);
        assertThat(response.required("id").asText()).isEqualTo(saved.getId().toString());
        assertThat(response.required("login").asText()).isEqualTo(login);
        assertThat(Instant.parse(response.required("createdAt").asText())).isEqualTo(saved.getCreatedAt());
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(password, saved.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(password.strip(), saved.getPasswordHash())).isFalse();
    }

    @Test
    void acceptsInclusiveLengthBoundaries() throws Exception {
        register("abc", "12345678").andExpect(status().isCreated());
        register("😀".repeat(64), "ą".repeat(36)).andExpect(status().isCreated());
    }

    @Test
    void registeringAnExistingLoginKeepsTheOriginalPassword() throws Exception {
        String login = "existing.user";
        register(login, "firstPassword").andExpect(status().isCreated());
        String originalHash = users.findByLogin(login).orElseThrow().getPasswordHash();

        register(login, "secondPassword").andExpect(status().isConflict());

        assertThat(users.findByLogin(login).orElseThrow().getPasswordHash()).isEqualTo(originalHash);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidInputBeforeSaving(Map<String, ?> request) throws Exception {
        long rowsBefore = users.count();
        String body = postRegistration(json.writeValueAsString(request)).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).required("detail").asText()).isEqualTo("Invalid login or password format.");
        assertThat(users.count()).isEqualTo(rowsBefore);
    }

    static Stream<Map<String, ?>> invalidRequests() {
        return Stream.of(
                Map.of("password", "12345678"),
                Map.of("login", "valid"),
                Map.of("login", "   ", "password", "12345678"),
                Map.of("login", "😀a", "password", "12345678"),
                Map.of("login", "a".repeat(65), "password", "12345678"),
                Map.of("login", "valid", "password", "1234567"),
                Map.of("login", "valid", "password", "😀".repeat(7)),
                Map.of("login", "valid", "password", "ą".repeat(36) + "x"),
                Map.of("login", 1234, "password", "12345678"),
                Map.of("login", "valid", "password", 12345678));
    }

    @Test
    void concurrentRequestsForTheSameLoginCreateOneAccount() throws Exception {
        long rowsBefore = users.count();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> registerAfter(start, "Racing.Login"));
            var second = executor.submit(() -> registerAfter(start, "racing.login"));
            start.countDown();

            var statuses = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(users.count()).isEqualTo(rowsBefore + 1);
    }

    private int registerAfter(CountDownLatch start, String login) throws Exception {
        start.await();
        return register(login, "password123").andReturn().getResponse().getStatus();
    }

    private ResultActions register(String login, String password) throws Exception {
        return postRegistration(json.writeValueAsString(Map.of("login", login, "password", password)));
    }

    private ResultActions postRegistration(String body) throws Exception {
        return mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
