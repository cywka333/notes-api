package com.example.notes.item;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemShareRevocationIT extends ItemHttpTestSupport {

    private static final String REVOKE = "/items/{id}/share/{userId}";

    @Test
    void revokesOnlyTheSpecifiedGrantAndAllowsTheOwnerToShareAgain() throws Exception {
        Account owner = account();
        Account editor = account();
        Account viewer = account();
        String first = create(owner, Map.of("title", "First")).required("id").asText();
        String second = create(owner, Map.of("title", "Second")).required("id").asText();
        share(owner, first, editor.id(), "EDITOR").andExpect(status().isCreated());
        share(owner, first, viewer.id(), "VIEWER").andExpect(status().isCreated());
        share(owner, second, editor.id(), "VIEWER").andExpect(status().isCreated());

        revoke(owner, first, editor.id()).andExpect(status().isNoContent()).andExpect(content().string(""));
        revoke(owner, first, editor.id()).andExpect(status().isNotFound());

        assertOnlyVisibleItem(editor, second, "VIEWER");
        assertOnlyVisibleItem(viewer, first, "VIEWER");
        assertThat(list(owner)).hasSize(2);
        share(owner, first, editor.id(), "VIEWER").andExpect(status().isCreated());
        assertThat(list(editor)).hasSize(2);
    }

    @Test
    void editorViewerAndStrangerCannotRevokeEvenWhenTheRecipientHasNoGrant() throws Exception {
        Account owner = account();
        Account editor = account();
        Account viewer = account();
        Account stranger = account();
        String id = create(owner, Map.of("title", "Owner manages permissions")).required("id").asText();
        share(owner, id, editor.id(), "EDITOR").andExpect(status().isCreated());
        share(owner, id, viewer.id(), "VIEWER").andExpect(status().isCreated());

        for (Account caller : List.of(editor, viewer, stranger)) {
            revoke(caller, id, viewer.id()).andExpect(status().isForbidden());
            revoke(caller, id, stranger.id()).andExpect(status().isForbidden());
        }
        assertOnlyVisibleItem(viewer, id, "VIEWER");
        assertOnlyVisibleItem(editor, id, "EDITOR");
    }

    @Test
    void missingGrantMissingItemAndSoftDeletedItemReturnNotFound() throws Exception {
        Account owner = account();
        Account viewer = account();
        String id = create(owner, Map.of("title", "Hidden")).required("id").asText();

        revoke(owner, id, viewer.id()).andExpect(status().isNotFound());
        revoke(owner, UUID.randomUUID().toString(), viewer.id()).andExpect(status().isNotFound());
        share(owner, id, viewer.id(), "VIEWER").andExpect(status().isCreated());
        deleteItem(owner, id).andExpect(status().isNoContent());
        revoke(owner, id, viewer.id()).andExpect(status().isNotFound());
    }

    @Test
    void missingOrInvalidTokenReturnsUnauthorized() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        mvc.perform(delete(REVOKE, id, userId)).andExpect(status().isUnauthorized());
        mvc.perform(delete(REVOKE, id, userId).header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void simultaneousRevocationsRemoveTheGrantOnce() throws Exception {
        Account owner = account();
        Account viewer = account();
        String id = create(owner, Map.of("title", "Concurrent revocation")).required("id").asText();
        share(owner, id, viewer.id(), "VIEWER").andExpect(status().isCreated());
        var start = new CyclicBarrier(2);
        Callable<Integer> attempt = () -> {
            start.await(10, TimeUnit.SECONDS);
            return revoke(owner, id, viewer.id()).andReturn().getResponse().getStatus();
        };

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 404);
        }
        assertThat(list(viewer)).isEmpty();
    }

    private void assertOnlyVisibleItem(Account account, String itemId, String role) throws Exception {
        JsonNode visible = list(account);
        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).required("id").asText()).isEqualTo(itemId);
        assertThat(visible.get(0).required("myRole").asText()).isEqualTo(role);
    }

    private ResultActions revoke(Account caller, String itemId, UUID recipientId) throws Exception {
        return mvc.perform(delete(REVOKE, itemId, recipientId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.token()));
    }
}
