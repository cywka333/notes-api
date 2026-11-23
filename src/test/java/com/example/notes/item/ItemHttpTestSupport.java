package com.example.notes.item;

import com.example.notes.support.MySqlTestConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.login-rate-limit.attempts=100")
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
abstract class ItemHttpTestSupport {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    ResultActions share(Account caller, String itemId, UUID recipientId, String role) throws Exception {
        return postShare(caller, itemId, json.writeValueAsString(Map.of("userId", recipientId, "role", role)));
    }

    ResultActions postShare(Account caller, String itemId, String request) throws Exception {
        return mvc.perform(post("/items/{id}/share", itemId).header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.token())
                .contentType(MediaType.APPLICATION_JSON).content(request));
    }

    Account account() throws Exception {
        String login = "item-" + UUID.randomUUID();
        String credentials = json.writeValueAsString(Map.of("login", login, "password", "test-password"));
        JsonNode registered = body(mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
                .content(credentials)).andExpect(status().isCreated()));
        JsonNode loggedIn = body(mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials)).andExpect(status().isOk()));
        return new Account(UUID.fromString(registered.required("id").asText()), login, loggedIn.required("token").asText());
    }

    ResultActions postItem(Account account, String request) throws Exception {
        return mvc.perform(post("/items").header(HttpHeaders.AUTHORIZATION, "Bearer " + account.token())
                .contentType(MediaType.APPLICATION_JSON).content(request));
    }

    JsonNode create(Account account, Map<String, ?> request) throws Exception {
        return body(postItem(account, json.writeValueAsString(request)).andExpect(status().isCreated()));
    }

    JsonNode list(Account account) throws Exception {
        return body(mvc.perform(get("/items").header(HttpHeaders.AUTHORIZATION, "Bearer " + account.token()))
                .andExpect(status().isOk()));
    }

    JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    record Account(UUID id, String login, String token) {
    }
}
