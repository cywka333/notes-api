package com.example.notes.item;

import com.example.notes.item.permission.ItemAccessPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemEditingIT extends ItemHttpTestSupport {

    private static final String PATH = "/items/{id}";

    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private ItemAccessPolicy access;

    @Test
    void ownerAndEditorCanPatchIndependentlyAndClearContent() throws Exception {
        Account owner = account();
        Account editor = account();
        JsonNode original = create(owner, Map.of("title", "Before", "content", "First text"));
        String id = original.required("id").asText();
        share(owner, id, editor.id(), "EDITOR").andExpect(status().isCreated());

        JsonNode first = body(edit(owner, id, Map.of("title", "After", "version", 0)).andExpect(status().isOk()));
        assertThat(first.size()).isEqualTo(5);
        assertThat(first.required("id")).isEqualTo(original.required("id"));
        assertThat(first.required("title").asText()).isEqualTo("After");
        assertThat(first.required("content").asText()).isEqualTo("First text");
        assertThat(first.required("version").intValue()).isOne();
        assertThat(Instant.parse(first.required("updatedAt").asText()))
                .isAfterOrEqualTo(Instant.parse(original.required("updatedAt").asText()));

        edit(editor, id, Map.of("content", "Second text", "version", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("After"))
                .andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.content").value("Second text"));

        JsonNode cleared = body(editRaw(owner, id, "{\"content\":null,\"version\":2}").andExpect(status().isOk()));
        assertThat(cleared.required("content").isNull()).isTrue();
        assertThat(cleared.required("version").intValue()).isEqualTo(3);
        assertThat(list(editor).get(0).required("content").isNull()).isTrue();
    }

    @Test
    void anUnchangedPatchPreservesTheTimestampVersionAndRevisionCount() throws Exception {
        Account owner = account();
        JsonNode original = create(owner, Map.of("title", "Stable"));
        String id = original.required("id").asText();
        for (Map<String, ?> patch : List.of(Map.of("version", 0), Map.of("title", "Stable", "version", 0))) {
            JsonNode same = body(edit(owner, id, patch).andExpect(status().isOk()));
            assertThat(same.required("version")).isEqualTo(original.required("version"));
            assertThat(same.required("updatedAt")).isEqualTo(original.required("updatedAt"));
        }
        assertThat(revisions(id)).isEqualTo(1);
    }

    @Test
    void aStaleVersionReturns409WithTheCommittedVersionAndAllowsAnExplicitRetry() throws Exception {
        Account owner = account();
        String id = create(owner, Map.of("title", "Original")).required("id").asText();
        edit(owner, id, Map.of("title", "Winner", "version", 0)).andExpect(status().isOk());
        edit(owner, id, Map.of("title", "Loser", "version", 0))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.currentVersion").value(1)).andExpect(jsonPath("$.status").value(409));
        assertThat(list(owner).get(0).required("title").asText()).isEqualTo("Winner");
        edit(owner, id, Map.of("title", "Reconciled", "version", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        assertThat(revisions(id)).isEqualTo(3);
    }

    @Test
    void rejectsMalformedPatchesBeforeChangingTheNote() throws Exception {
        Account owner = account();
        String id = create(owner, Map.of("title", "Stable")).required("id").asText();
        for (String payload : List.of("{}", "{", "{\"version\":null}", "{\"title\":\"No version\"}",
                "{\"version\":-1}", "{\"version\":0.7}", "{\"version\":\"0\"}", "{\"version\":2147483648}",
                "{\"title\":null,\"version\":0}", "{\"title\":\"  \",\"version\":0}", "{\"title\":7,\"version\":0}",
                "{\"content\":false,\"version\":0}", json.writeValueAsString(Map.of("version", 0, "title", "a".repeat(256))),
                json.writeValueAsString(Map.of("version", 0, "content", "ą".repeat(32_768))))) {
            editRaw(owner, id, payload).andExpect(status().isBadRequest());
        }
        assertThat(list(owner).get(0).required("version").intValue()).isZero();
    }

    @Test
    void viewerStrangerAndRevokedEditorCannotEditOrDiscoverTheVersion() throws Exception {
        Account owner = account();
        Account member = account();
        Account stranger = account();
        String id = create(owner, Map.of("title", "Private")).required("id").asText();
        share(owner, id, member.id(), "EDITOR").andExpect(status().isCreated());
        share(owner, id, member.id(), "VIEWER").andExpect(status().isOk());
        for (Account denied : List.of(member, stranger)) {
            edit(denied, id, Map.of("title", "Denied", "version", 8))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.currentVersion").doesNotExist());
        }
        assertThat(list(owner).get(0).required("title").asText()).isEqualTo("Private");
    }

    @Test
    void requiresAValidTokenAndAnActiveItem() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(patch(PATH, missing).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(PATH, missing).header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isUnauthorized());

        Account owner = account();
        edit(owner, missing.toString(), Map.of("version", 0)).andExpect(status().isNotFound());
        String id = create(owner, Map.of("title", "Deleted")).required("id").asText();
        deleteItem(owner, id).andExpect(status().isNoContent());
        edit(owner, id, Map.of("title", "Restore", "version", 0)).andExpect(status().isNotFound());
    }

    @Test
    void twoWritersThatBothReadVersionZeroProduceOneRevisionAndOneConflict() throws Exception {
        Account owner = account();
        Account editor = account();
        String id = create(owner, Map.of("title", "Before")).required("id").asText();
        share(owner, id, editor.id(), "EDITOR").andExpect(status().isCreated());

        // Let both real requests read version zero before either can write to MySQL.
        var readers = new CyclicBarrier(2);
        var checks = new AtomicInteger();
        doAnswer(call -> {
            call.callRealMethod();
            if (checks.incrementAndGet() <= 2) {
                readers.await(10, TimeUnit.SECONDS);
            }
            return null;
        }).when(access).requireEdit(any(), any());

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> edit(owner, id, Map.of("title", "Owner edit", "version", 0)).andReturn().getResponse());
            var second = pool.submit(() -> edit(editor, id, Map.of("title", "Editor edit", "version", 0)).andReturn().getResponse());
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.getStatus(), b.getStatus())).containsExactlyInAnyOrder(200, 409);
            var winner = a.getStatus() == 200 ? a : b;
            var conflict = a.getStatus() == 409 ? a : b;
            assertThat(json.readTree(conflict.getContentAsByteArray()).required("currentVersion").intValue()).isOne();
            assertThat(json.readTree(winner.getContentAsByteArray()).required("title"))
                    .isEqualTo(list(owner).get(0).required("title"));
        }
        assertThat(revisions(id)).isEqualTo(2); // ADD and the winning MOD
    }

    @Test
    void anEditAlreadyInFlightCannotRestoreADeletedItem() throws Exception {
        Account owner = account();
        Account editor = account();
        String id = create(owner, Map.of("title", "Before")).required("id").asText();
        share(owner, id, editor.id(), "EDITOR").andExpect(status().isCreated());

        var editorHasRead = new CountDownLatch(1);
        var ownerHasDeleted = new CountDownLatch(1);
        doAnswer(call -> {
            call.callRealMethod();
            editorHasRead.countDown();
            assertThat(ownerHasDeleted.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(access).requireEdit(any(), any());

        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> edit(editor, id, Map.of("title", "Late edit", "version", 0)));
            try {
                assertThat(editorHasRead.await(10, TimeUnit.SECONDS)).isTrue();
                deleteItem(owner, id).andExpect(status().isNoContent());
            } finally {
                ownerHasDeleted.countDown();
            }
            pending.get(15, TimeUnit.SECONDS).andExpect(status().isNotFound());
        }
        assertThat(list(owner)).isEmpty();
        assertThat(revisions(id)).isEqualTo(2); // ADD and DEL
    }

    private int revisions(String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM items_aud WHERE id = UUID_TO_BIN(?)", Integer.class, id);
    }

    private ResultActions edit(Account account, String id, Map<String, ?> request) throws Exception {
        return editRaw(account, id, json.writeValueAsString(request));
    }

    private ResultActions editRaw(Account account, String id, String request) throws Exception {
        return mvc.perform(patch(PATH, id).header(HttpHeaders.AUTHORIZATION, "Bearer " + account.token())
                .contentType(MediaType.APPLICATION_JSON).content(request));
    }
}
