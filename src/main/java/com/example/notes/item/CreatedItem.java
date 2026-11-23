package com.example.notes.item;

import java.time.Instant;
import java.util.UUID;

record CreatedItem(UUID id, String title, String content, Integer version, UUID ownerId,
                   Instant createdAt, Instant updatedAt) {

    static CreatedItem from(Item item) {
        return new CreatedItem(item.getId(), item.getTitle(), item.getContent(), item.getVersion(),
                item.getOwner().getId(), item.getCreatedAt(), item.getUpdatedAt());
    }
}
