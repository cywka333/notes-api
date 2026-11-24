package com.example.notes.auth.web;

import java.time.Instant;
import java.util.UUID;

record RegisterResponse(UUID id, String login, Instant createdAt) {
}
