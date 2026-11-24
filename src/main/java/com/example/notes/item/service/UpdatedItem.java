package com.example.notes.item.service;

import com.example.notes.item.model.Item;

import java.time.Instant;
import java.util.UUID;

public record UpdatedItem(UUID id, String title, String content, int version, Instant updatedAt) {

    static UpdatedItem from(Item item) {
        return new UpdatedItem(item.getId(), item.getTitle(), item.getContent(), item.getVersion(), item.getUpdatedAt());
    }
}
