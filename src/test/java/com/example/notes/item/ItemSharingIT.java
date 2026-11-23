package com.example.notes.item;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemSharingIT extends ItemHttpTestSupport {

    @Test
    void createsThenUpdatesTheGrantAndReturnsThePersistedTimestamp() throws Exception {
        Account owner = account();
        Account recipient = account();
        String id = create(owner, Map.of("title", "Shared note")).required("id").asText();

        JsonNode first = body(share(owner, id, recipient.id(), "VIEWER").andExpect(status().isCreated()));
        assertThat(first.size()).isEqualTo(4);
        assertThat(first.required("itemId").asText()).isEqualTo(id);
        assertThat(first.required("userId").asText()).isEqualTo(recipient.id().toString());
        assertThat(first.required("role").asText()).isEqualTo("VIEWER");
        Instant grantedAt = Instant.parse(first.required("grantedAt").asText());

        for (String role : List.of("EDITOR", "EDITOR", "VIEWER")) {
            JsonNode updated = body(share(owner, id, recipient.id(), role).andExpect(status().isOk()));
            assertThat(updated.size()).isEqualTo(4);
            assertThat(updated.required("itemId")).isEqualTo(first.required("itemId"));
            assertThat(updated.required("userId")).isEqualTo(first.required("userId"));
            assertThat(updated.required("role").asText()).isEqualTo(role);
            assertThat(Instant.parse(updated.required("grantedAt").asText())).isEqualTo(grantedAt);
            JsonNode visible = list(recipient);
            assertThat(visible).hasSize(1);
            assertThat(visible.get(0).required("myRole").asText()).isEqualTo(role);
            assertThat(visible.get(0).required("version").asInt()).isZero();
        }
    }

    @Test
    void ownerCannotShareWithSelfAndOtherCallersCannotGrantAccess() throws Exception {
        Account owner = account();
        Account member = account();
        Account stranger = account();
        String id = create(owner, Map.of("title", "Private")).required("id").asText();

        share(owner, id, owner.id(), "EDITOR").andExpect(status().isBadRequest());
        share(stranger, id, stranger.id(), "EDITOR").andExpect(status().isForbidden());
        share(owner, id, member.id(), "VIEWER").andExpect(status().isCreated());
        share(member, id, stranger.id(), "EDITOR").andExpect(status().isForbidden());
        share(owner, id, member.id(), "EDITOR").andExpect(status().isOk());
        share(member, id, stranger.id(), "EDITOR").andExpect(status().isForbidden());
        assertThat(list(stranger)).isEmpty();
    }

    @Test
    void unknownItemsAndUnknownRecipientsReturnNotFound() throws Exception {
        Account owner = account();
        String id = create(owner, Map.of("title", "Test")).required("id").asText();
        UUID unknownUser = UUID.randomUUID();

        share(owner, id, unknownUser, "VIEWER").andExpect(status().isNotFound());
        share(owner, UUID.randomUUID().toString(), owner.id(), "VIEWER").andExpect(status().isNotFound());
    }

    @Test
    void malformedRequestsCannotGrantAccess() throws Exception {
        Account owner = account();
        Account recipient = account();
        String id = create(owner, Map.of("title", "Validation")).required("id").asText();
        for (Object role : List.of("OWNER", "viewer", "", 0, "1", false)) {
            postShare(owner, id, json.writeValueAsString(Map.of("userId", recipient.id(), "role", role)))
                    .andExpect(status().isBadRequest());
        }
        for (String request : List.of("{}", "{\"role\":\"VIEWER\"}", "{\"userId\":\"" + recipient.id() + "\"}",
                "{\"userId\":null,\"role\":\"EDITOR\"}", "{\"userId\":\"invalid\",\"role\":\"VIEWER\"}")) {
            postShare(owner, id, request).andExpect(status().isBadRequest());
        }
        assertThat(list(recipient)).isEmpty();
    }

    @Test
    void missingOrInvalidBearerTokenReturnsUnauthorized() throws Exception {
        String id = UUID.randomUUID().toString();
        String request = json.writeValueAsString(Map.of("userId", UUID.randomUUID(), "role", "VIEWER"));
        mvc.perform(post("/items/{id}/share", id).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/items/{id}/share", id).header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentFirstGrantsProduceOneCreationAndOneUpdate() throws Exception {
        Account owner = account();
        Account recipient = account();
        String id = create(owner, Map.of("title", "Concurrent")).required("id").asText();
        var start = new CyclicBarrier(2);
        Callable<Integer> grant = () -> {
            start.await(10, TimeUnit.SECONDS);
            return share(owner, id, recipient.id(), "VIEWER").andReturn().getResponse().getStatus();
        };

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(grant);
            var second = pool.submit(grant);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 201);
        }
        assertThat(list(recipient)).hasSize(1);
    }
}
