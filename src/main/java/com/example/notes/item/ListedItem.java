package com.example.notes.item;

import java.time.Instant;
import java.util.UUID;

public record ListedItem(UUID id, String title, String content, Integer version, UUID ownerId,
                         String myRole, Instant updatedAt) {
}