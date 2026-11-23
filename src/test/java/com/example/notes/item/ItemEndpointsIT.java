package com.example.notes.item;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemEndpointsIT extends ItemHttpTestSupport {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtEncoder encoder;

    @Test
    void createsAnAuditedNoteForTheAuthenticatedOwner() throws Exception {
        Account caller = account();
        Account other = account();
        String title = "📝".repeat(255);
        JsonNode created = create(caller, Map.of("title", title, "content", "Pierwsza treść",
                "ownerId", other.id(), "version", 42, "myRole", "EDITOR"));
        UUID id = UUID.fromString(created.required("id").asText());

        assertThat(created.size()).isEqualTo(7);
        assertThat(created.required("title").asText()).isEqualTo(title);
        assertThat(created.required("content").asText()).isEqualTo("Pierwsza treść");
        assertThat(created.required("ownerId").asText()).isEqualTo(caller.id().toString());
        assertThat(created.required("version").intValue()).isZero();
        Instant createdAt = Instant.parse(created.required("createdAt").asText());
        assertThat(Instant.parse(created.required("updatedAt").asText())).isEqualTo(createdAt);
        assertThat(jdbc.queryForObject("""
                SELECT r.changed_by FROM items_aud a JOIN item_revisions r ON r.id = a.rev
                WHERE a.id = UUID_TO_BIN(?) AND a.revtype = 0
                """, String.class, id.toString())).isEqualTo(caller.login());
        assertThat(list(other)).isEmpty();
    }

    @Test
    void acceptsOmittedNullAndEmptyContent() throws Exception {
        Account caller = account();
        assertThat(create(caller, Map.of("title", "A")).required("content").isNull()).isTrue();
        JsonNode explicitNull = body(postItem(caller, "{\"title\":\"B\",\"content\":null}")
                .andExpect(status().isCreated()));
        assertThat(explicitNull.required("content").isNull()).isTrue();
        assertThat(create(caller, Map.of("title", "C", "content", "")).required("content").textValue()).isEmpty();
    }

    @Test
    void rejectsInvalidJsonAndTitlesBeforeSaving() throws Exception {
        Account caller = account();
        for (String request : List.of("{", "{}", "{\"title\":null}", "{\"title\":\"\"}", "{\"title\":\"   \"}",
                "{\"title\":123}", "{\"title\":\"A\",\"content\":false}",
                json.writeValueAsString(Map.of("title", "📝".repeat(256))))) {
            postItem(caller, request).andExpect(status().isBadRequest());
        }
        assertThat(list(caller)).isEmpty();
    }

    @Test
    void validatesTextCapacityInUtf8Bytes() throws Exception {
        Account caller = account();
        String fits = "ą".repeat(32_767) + "x";
        assertThat(create(caller, Map.of("title", "A", "content", fits)).required("content").textValue())
                .isEqualTo(fits);
        postItem(caller, json.writeValueAsString(Map.of("title", "B", "content", fits + "x")))
                .andExpect(status().isBadRequest());
        assertThat(list(caller)).hasSize(1);
    }

    @Test
    void listsOnlyActiveOwnedOrSharedNotesWithTheCallersRole() throws Exception {
        Account caller = account();
        Account other = account();
        assertThat(list(caller)).isEmpty();

        JsonNode mine = create(caller, Map.of("title", "Mine", "content", "My text"));
        JsonNode editable = create(other, Map.of("title", "Shared for editing"));
        JsonNode readable = create(other, Map.of("title", "Shared for reading"));
        JsonNode hiddenOwned = create(caller, Map.of("title", "Deleted by me"));
        JsonNode hiddenShared = create(other, Map.of("title", "Deleted after sharing"));
        create(other, Map.of("title", "Private"));

        share(caller, mine.required("id").asText(), other.id(), "EDITOR").andExpect(status().isCreated());
        share(other, editable.required("id").asText(), caller.id(), "EDITOR").andExpect(status().isCreated());
        share(other, readable.required("id").asText(), caller.id(), "VIEWER").andExpect(status().isCreated());
        share(other, hiddenShared.required("id").asText(), caller.id(), "VIEWER").andExpect(status().isCreated());
        deleteItem(caller, hiddenOwned.required("id").asText()).andExpect(status().isNoContent());
        deleteItem(other, hiddenShared.required("id").asText()).andExpect(status().isNoContent());

        share(other, hiddenShared.required("id").asText(), caller.id(), "EDITOR").andExpect(status().isNotFound());

        JsonNode visible = list(caller);
        assertThat(visible).hasSize(3);
        assertListEntry(visible.get(0), readable, "VIEWER");
        assertListEntry(visible.get(1), editable, "EDITOR");
        assertListEntry(visible.get(2), mine, "OWNER");
    }

    @Test
    void bothEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/items")).andExpect(status().isUnauthorized());
        mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"A\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsASignedTokenWithANonUuidSubject() throws Exception {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("urn:notes-api").subject("invalid-subject")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        mvc.perform(get("/items").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private static void assertListEntry(JsonNode entry, JsonNode created, String expectedRole) {
        assertThat(entry.size()).isEqualTo(7);
        for (String key : List.of("id", "ownerId", "title", "content", "version", "updatedAt")) {
            assertThat(entry.required(key)).as(key).isEqualTo(created.required(key));
        }
        assertThat(entry.required("myRole").asText()).isEqualTo(expectedRole);
    }
}
