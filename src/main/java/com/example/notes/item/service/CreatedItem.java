package com.example.notes.item.service;

import com.example.notes.item.model.Item;

import java.time.Instant;
import java.util.UUID;

public record CreatedItem(UUID id, String title, String content, Integer version, UUID ownerId,
                   Instant createdAt, Instant updatedAt) {

    static CreatedItem from(Item item) {
        return new CreatedItem(item.getId(), item.getTitle(), item.getContent(), item.getVersion(),
                item.getOwner().getId(), item.getCreatedAt(), item.getUpdatedAt());
    }
}
