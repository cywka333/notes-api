package com.example.notes.auth;

import com.example.notes.web.JsonStringDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.CodePointLength;

import java.nio.charset.StandardCharsets;

record RegisterRequest(
        @JsonDeserialize(using = JsonStringDeserializer.class)
        @NotBlank @CodePointLength(min = 3, max = 64) String login,
        @JsonDeserialize(using = JsonStringDeserializer.class)
        @NotNull @CodePointLength(min = 8) String password
) {
    @JsonIgnore
    @AssertTrue(message = "Password must fit within 72 UTF-8 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() {
        return "RegisterRequest[login=" + login + "]";
    }
}
