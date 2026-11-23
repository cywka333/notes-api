package com.example.notes.item;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

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

class ItemDeletionIT extends ItemHttpTestSupport {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void ownerSoftDeletesTheNoteAndEnversRecordsOneDeletionByThatOwner() throws Exception {
        Account owner = account();
        Account editor = account();
        Account viewer = account();
        String id = create(owner, Map.of("title", "Shared draft", "content", "Original text")).required("id").asText();
        share(owner, id, editor.id(), "EDITOR").andExpect(status().isCreated());
        share(owner, id, viewer.id(), "VIEWER").andExpect(status().isCreated());

        deleteItem(owner, id).andExpect(status().isNoContent()).andExpect(content().string(""));
        deleteItem(owner, id).andExpect(status().isNotFound());

        assertThat(list(owner)).isEmpty();
        assertThat(list(editor)).isEmpty();
        assertThat(list(viewer)).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT version FROM items WHERE id = UUID_TO_BIN(?) AND deleted = true AND content = ?
                """, Integer.class, id, "Original text")).isOne();
        // queryForObject also checks that the two requests created exactly one DEL revision.
        assertThat(jdbc.queryForObject("""
                SELECT r.changed_by FROM items_aud a JOIN item_revisions r ON r.id = a.rev
                WHERE a.id = UUID_TO_BIN(?) AND a.revtype = 2
                """, String.class, id)).isEqualTo(owner.login());
    }

    @Test
    void editorViewerAndStrangerAreForbiddenAndTheNoteStaysVisible() throws Exception {
        Account owner = account();
        Account editor = account();
        Account viewer = account();
        Account stranger = account();
        String id = create(owner, Map.of("title", "Owner only")).required("id").asText();
        share(owner, id, editor.id(), "EDITOR").andExpect(status().isCreated());
        share(owner, id, viewer.id(), "VIEWER").andExpect(status().isCreated());

        for (Account caller : List.of(editor, viewer, stranger)) {
            deleteItem(caller, id).andExpect(status().isForbidden());
        }
        var visible = list(owner);
        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).required("id").asText()).isEqualTo(id);
        assertThat(visible.get(0).required("version").intValue()).isZero();
    }

    @Test
    void authenticationIsRequiredAndAMissingItemReturnsNotFound() throws Exception {
        String missing = UUID.randomUUID().toString();
        mvc.perform(delete("/items/{id}", missing)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/items/{id}", missing).header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        deleteItem(account(), missing).andExpect(status().isNotFound());
    }

    @Test
    void simultaneousOwnerDeletesSucceedOnce() throws Exception {
        Account owner = account();
        String id = create(owner, Map.of("title", "Once")).required("id").asText();
        var start = new CyclicBarrier(2);
        Callable<Integer> request = () -> {
            start.await(10, TimeUnit.SECONDS);
            return deleteItem(owner, id).andReturn().getResponse().getStatus();
        };

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(request);
            var second = pool.submit(request);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 404);
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM items_aud WHERE id = UUID_TO_BIN(?) AND revtype = 2
                """, Integer.class, id)).isOne();
    }
}
