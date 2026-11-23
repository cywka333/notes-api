package com.example.notes.item;

import java.time.Instant;
import java.util.UUID;

record UpdatedItem(UUID id, String title, String content, int version, Instant updatedAt) {

    static UpdatedItem from(Item item) {
        return new UpdatedItem(item.getId(), item.getTitle(), item.getContent(), item.getVersion(), item.getUpdatedAt());
    }
}
