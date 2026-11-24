package com.example.notes.item.history;

import com.example.notes.support.MySqlTestConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.login-rate-limit.attempts=100")
@Import(MySqlTestConfiguration.class)
class ItemHistoryIT {

    @Autowired private TestRestTemplate http;

    @Test
    void returnsEveryCommittedSnapshotInOrderWithItsActualAuthor() {
        Account owner = account();
        Account editor = account();
        Account viewer = account();
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String note = createNote(owner, "Draft");
        share(owner, note, editor, "EDITOR");
        share(owner, note, viewer, "VIEWER");

        request(PATCH, note, owner.token(), Map.of("version", 0, "title", "Meeting notes"), OK);
        createNote(editor, "Separate note with its own revision");
        request(PATCH, note, editor.token(), Map.of("version", 1, "content", "First decision"), OK);
        request(PATCH, note, owner.token(), Map.of("version", 2, "content", "Final decision"), OK);
        request(PATCH, note, editor.token(), Map.of("version", 1, "content", "Stale edit"), HttpStatus.CONFLICT);

        String historyPath = note + "/history";
        JsonNode history = request(GET, historyPath, viewer.token(), null, OK);
        assertThat(history.isArray()).isTrue();
        assertThat(history).hasSize(4);
        assertSnapshot(history.get(0), "ADD", owner.login(), "Draft", null);
        assertSnapshot(history.get(1), "MOD", owner.login(), "Meeting notes", null);
        assertSnapshot(history.get(2), "MOD", editor.login(), "Meeting notes", "First decision");
        assertSnapshot(history.get(3), "MOD", owner.login(), "Meeting notes", "Final decision");

        long previousRevision = 0;
        Instant end = Instant.now();
        for (JsonNode entry : history) {
            JsonNode revision = entry.required("revision");
            assertThat(revision.isIntegralNumber()).isTrue();
            assertThat(revision.longValue()).isGreaterThan(previousRevision);
            previousRevision = revision.longValue();
            assertThat(Instant.parse(entry.required("timestamp").textValue())).isBetween(start, end);
        }
        for (Account reader : List.of(owner, editor)) {
            assertThat(request(GET, historyPath, reader.token(), null, OK)).isEqualTo(history);
        }
    }

    @Test
    void formerEditorLosesAccessToHistoryEvenAfterAuthoringARevision() {
        Account owner = account();
        Account editor = account();
        String note = createNote(owner, "Private");
        String history = note + "/history";
        request(GET, history, editor.token(), null, FORBIDDEN);

        share(owner, note, editor, "EDITOR");
        request(PATCH, note, editor.token(), Map.of("version", 0, "title", "Edited"), OK);
        JsonNode beforeRevocation = request(GET, history, editor.token(), null, OK);
        assertThat(beforeRevocation.get(1).required("changedBy").textValue()).isEqualTo(editor.login());

        request(DELETE, note + "/share/" + editor.id(), owner.token(), null, NO_CONTENT);
        request(GET, history, editor.token(), null, FORBIDDEN);
        assertThat(request(GET, history, owner.token(), null, OK)).isEqualTo(beforeRevocation);
    }

    @Test
    void historyRequiresAuthenticationAndAnActiveNote() {
        Account owner = account();
        String note = createNote(owner, "To be deleted");
        String history = note + "/history";

        request(GET, history, null, null, UNAUTHORIZED);
        request(GET, history, "invalid-token", null, UNAUTHORIZED);
        request(GET, "/items/" + UUID.randomUUID() + "/history", owner.token(), null, NOT_FOUND);
        request(DELETE, note, owner.token(), null, NO_CONTENT);
        request(GET, history, owner.token(), null, NOT_FOUND);
    }

    private static void assertSnapshot(JsonNode entry, String type, String author, String title, String content) {
        assertThat(entry.required("revisionType").textValue()).isEqualTo(type);
        assertThat(entry.required("changedBy").textValue()).isEqualTo(author);
        assertThat(entry.required("title").textValue()).isEqualTo(title);
        JsonNode value = entry.required("content");
        if (content == null) {
            assertThat(value.isNull()).isTrue();
        } else {
            assertThat(value.textValue()).isEqualTo(content);
        }
    }

    private Account account() {
        String login = "history-" + UUID.randomUUID();
        var credentials = Map.of("login", login, "password", "test-password");
        JsonNode user = request(POST, "/register", null, credentials, CREATED);
        JsonNode session = request(POST, "/login", null, credentials, OK);
        return new Account(user.required("id").textValue(), login, session.required("token").textValue());
    }

    private String createNote(Account owner, String title) {
        JsonNode note = request(POST, "/items", owner.token(), Map.of("title", title), CREATED);
        return "/items/" + note.required("id").textValue();
    }

    private void share(Account owner, String note, Account recipient, String role) {
        request(POST, note + "/share", owner.token(), Map.of("userId", recipient.id(), "role", role), CREATED);
    }

    private JsonNode request(HttpMethod method, String path, String token, Object body, HttpStatus expected) {
        var headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        var response = http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode()).as("%s %s", method, path).isEqualTo(expected);
        return response.getBody();
    }

    private record Account(String id, String login, String token) {
    }
}
