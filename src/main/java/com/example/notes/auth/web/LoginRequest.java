package com.example.notes.auth.web;

import com.example.notes.web.JsonStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

record LoginRequest(
        @JsonDeserialize(using = JsonStringDeserializer.class) @NotBlank String login,
        @JsonDeserialize(using = JsonStringDeserializer.class) @NotEmpty String password
) {
    @Override
    public String toString() {
        return "LoginRequest[login=" + login + "]";
    }
}
