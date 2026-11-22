package com.example.notes.auth;

import com.example.notes.support.MySqlTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.login-rate-limit.attempts=5", "app.login-rate-limit.window-seconds=60"})
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class LoginRateLimitIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void sixthAttemptIsRejectedWithRetryAfterAndOtherIpsCanStillLogIn() throws Exception {
        String firstIp = "192.0.2.10";
        String secondIp = "192.0.2.11";
        String credentials = """
                {"login":"rate.limit.user","password":"rate-limit-password"}
                """;

        postFrom(firstIp, "/register", credentials).andExpect(status().isCreated());
        postFrom(firstIp, "/login", credentials).andExpect(status().isOk());
        postFrom(firstIp, "/login", "{}").andExpect(status().isBadRequest());
        for (int i = 0; i < 3; i++) {
            postFrom(firstIp, "/login", """
                    {"login":"rate.limit.user","password":"wrong"}
                    """).andExpect(status().isUnauthorized());
        }

        var response = postFrom(firstIp, "/login", credentials)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse();
        assertThat(Long.parseLong(response.getHeader(HttpHeaders.RETRY_AFTER))).isBetween(1L, 60L);

        postFrom(secondIp, "/login", credentials).andExpect(status().isOk());
        postFrom(firstIp, "/register", "{}").andExpect(status().isBadRequest());
    }

    private ResultActions postFrom(String ip, String path, String body) throws Exception {
        return mvc.perform(post(path).with(request -> {
            request.setRemoteAddr(ip);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
