package com.example.notes.auth;

import java.time.Instant;
import java.util.UUID;

record RegisterResponse(UUID id, String login, Instant createdAt) {
}
